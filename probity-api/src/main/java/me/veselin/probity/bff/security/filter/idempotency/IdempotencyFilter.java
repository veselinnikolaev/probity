package me.veselin.probity.bff.security.filter.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.common.util.ApiRoutes;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Filter that provides idempotency for POST requests using Redis-backed state management.
 * <p>
 * This filter ensures that repeated requests with the same idempotency key return the same response,
 * preventing duplicate operations (e.g., creating the same portfolio twice).
 * <p>
 * <b>How it works:</b>
 * <ol>
 *   <li>Client includes {@code Idempotency-Key} header in POST request</li>
 *   <li>Filter validates key format (length and pattern from configuration)</li>
 *   <li>Filter atomically checks Redis for existing state</li>
 *   <li>If key exists and is PENDING: returns 409 Conflict (request in progress)</li>
 *   <li>If key exists and is COMPLETED: returns cached response with {@code X-Cache: Idempotent-Hit}</li>
 *   <li>If key doesn't exist: creates PENDING state, processes request, caches successful (2xx) responses</li>
 * </ol>
 * <p>
 * <b>State transitions:</b>
 * <ul>
 *   <li>PENDING → COMPLETED: on successful (2xx) response</li>
 *   <li>PENDING → deleted: on error response or exception (allows retry)</li>
 * </ul>
 * <p>
 * <b>Key format:</b> {@code {keyPrefix}{userId}:{clientKey}}
 * <p>
 * <b>Configuration:</b> TTL, key prefix, and validation rules are configurable via {@code probity.idempotency.*} properties.
 * <p>
 * <b>Excluded paths:</b> Auth endpoints and AI assistant chat do not require idempotency.
 *
 * @see IdempotencyRecord
 * @see IdempotencyStatus
 * @see IdempotencyProperties
 */
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IdempotencyProperties properties;


    /**
     * Paths that should be excluded from idempotency checks.
     * Auth endpoints and AI assistant chat should not require idempotency.
     */
    private static final List<String> IDEMPOTENCY_EXCLUDED_PATHS = Arrays.asList(
            ApiRoutes.Auth.LOGIN,
            ApiRoutes.Auth.REGISTER,
            ApiRoutes.Auth.LOGOUT,
            ApiRoutes.Auth.REFRESH,
            ApiRoutes.Auth.CSRF,
            ApiRoutes.Auth.VERIFY,
            ApiRoutes.Auth.RESEND_VERIFICATION,
            ApiRoutes.Assistant.CHAT
    );

    /**
     * Filters POST requests to enforce idempotency.
     * <p>
     * Non-POST requests pass through unchanged. POST requests require:
     * <ul>
     *   <li>{@code Idempotency-Key} header (required)</li>
     *   <li>Authenticated user (required)</li>
     * </ul>
     * <p>
     * Auth endpoints and AI assistant chat are excluded from idempotency requirements.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Only apply idempotency to POST requests
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip idempotency for excluded paths (auth endpoints, assistant chat)
        String requestPath = request.getRequestURI();
        if (IDEMPOTENCY_EXCLUDED_PATHS.contains(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Require Idempotency-Key header for all POST requests
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Required header 'Idempotency-Key' is missing.");
            return;
        }

        // Validate idempotency key format
        if (!isValidIdempotencyKey(idempotencyKey)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    String.format("Invalid 'Idempotency-Key' format. Must be %d-%d characters and match pattern: %s",
                            properties.getKey().getMinLength(),
                            properties.getKey().getMaxLength(),
                            properties.getKey().getPattern()));
            return;
        }

        // Require authenticated user to scope keys
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User authentication required.");
            return;
        }

        // Scope key to the authenticated user ID to prevent cross-user conflicts
        String storageKey = properties.getKeyPrefix() + principal.id() + ":" + idempotencyKey;

        // 1. ATOMIC LOCKING: Try to create a PENDING state
        // setIfAbsent uses the Redis SETNX command. It returns true ONLY if the key didn't exist.
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                storageKey,
                new IdempotencyRecord(IdempotencyStatus.PENDING, 0, null),
                properties.getTtl().getHours(),
                TimeUnit.HOURS
        );

        if (Boolean.FALSE.equals(lockAcquired)) {
            // Key already exists! Determine if it's currently processing or complete.
            IdempotencyRecord existingRecord = (IdempotencyRecord) redisTemplate.opsForValue().get(storageKey);

            if (existingRecord != null) {
                if (existingRecord.status() == IdempotencyStatus.PENDING
                        || existingRecord.status() == IdempotencyStatus.PROCESSING) {
                    // Request is currently being processed (sync or async) - reject duplicate
                    response.sendError(HttpStatus.CONFLICT.value(), "An identical request is currently processing.");
                    return;
                } else if (existingRecord.status() == IdempotencyStatus.COMPLETED) {
                    // Cache Hit: Instantly replay the cached response
                    response.setStatus(existingRecord.statusCode());
                    response.setContentType("application/json");
                    response.addHeader("X-Cache", "Idempotent-Hit");
                    response.getWriter().write(existingRecord.responseBody());
                    return;
                }
            }
            // Fallback safety if record parsing failed
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error processing idempotency validation.");
            return;
        }

        // 2. Wrap the response to capture the response body for caching
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);

            int status = responseWrapper.getStatus();
            // Cache only successful responses (2xx status codes)
            // Errors (4xx, 5xx) delete the key to allow client to fix and retry
            if (status >= 200 && status < 300) {
                byte[] content = responseWrapper.getContentAsByteArray();
                String responseBody = new String(content, responseWrapper.getCharacterEncoding());

                // Update key status to COMPLETED with response details
                redisTemplate.opsForValue().set(
                        storageKey,
                        new IdempotencyRecord(IdempotencyStatus.COMPLETED, status, responseBody),
                        properties.getTtl().getHours(),
                        TimeUnit.HOURS
                );
            } else {
                // Validation error (4xx) or server error (5xx) - remove lock to allow retry
                redisTemplate.delete(storageKey);
            }

        } catch (Exception e) {
            // Unhandled controller exception - purge key to allow retry
            redisTemplate.delete(storageKey);
            throw e;
        } finally {
            // Ensure response content is sent to the client
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * Validates the format of an idempotency key.
     *
     * @param key the idempotency key to validate
     * @return true if the key is valid, false otherwise
     */
    private boolean isValidIdempotencyKey(String key) {
        int length = key.length();
        if (length < properties.getKey().getMinLength() || length > properties.getKey().getMaxLength()) {
            return false;
        }
        return Pattern.matches(properties.getKey().getPattern(), key);
    }
}