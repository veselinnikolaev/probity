package me.veselin.probity.bff.security.filter.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.bff.security.filter.jwt.UserPrincipal;
import me.veselin.probity.common.util.ApiRoutes;
import me.veselin.probity.simulation.dto.SimulationStatusResponse;
import me.veselin.probity.simulation.port.SimulationPort;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final java.util.UUID UUID_SENTINEL =
      java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final List<String> IDEMPOTENCY_EXCLUDED_PATHS =
      Arrays.asList(
          ApiRoutes.Auth.LOGIN,
          ApiRoutes.Auth.REGISTER,
          ApiRoutes.Auth.LOGOUT,
          ApiRoutes.Auth.LOGOUT_WITH_REFRESH,
          ApiRoutes.Auth.REFRESH,
          ApiRoutes.Auth.CSRF,
          ApiRoutes.Auth.VERIFY,
          ApiRoutes.Auth.RESEND_VERIFICATION,
          ApiRoutes.Assistant.CHAT);

  private final RedisTemplate<String, Object> redisTemplate;
  private final IdempotencyProperties properties;
  private final DurableIdempotencyService durableService;
  private final PlatformTransactionManager transactionManager;
  private final ObjectMapper objectMapper;
  private final SimulationPort simulationPort;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!"POST".equalsIgnoreCase(request.getMethod())
        || IDEMPOTENCY_EXCLUDED_PATHS.contains(request.getRequestURI())
        ) {
      filterChain.doFilter(request, response);
      return;
    }

    if (properties.isEnabled()
        && ApiRoutes.Simulations.RUN_ASYNC.equals(request.getRequestURI())) {
      durableFilter(request, response, filterChain);
      return;
    }
    legacyRedisFilter(request, response, filterChain);
  }

  private void durableFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    String key = request.getHeader("Idempotency-Key");
    if (key == null || key.isBlank()) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Required header 'Idempotency-Key' is missing.");
      return;
    }
    if (!isValidIdempotencyKey(key)) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid 'Idempotency-Key' format.");
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "User authentication required.");
      return;
    }

    byte[] body = request.getInputStream().readAllBytes();
    String hash = requestHash(request.getMethod(), request.getRequestURI(), body);
    java.util.UUID scopeId = scopeId(body);
    IdempotencyClaimDecision decision;
    try {
      decision =
          durableService.claim(
              principal.id(),
              "SIMULATION_RUN",
              scopeId,
              key,
              request.getMethod(),
              request.getRequestURI(),
              hash,
              properties.getLease(),
              Duration.ofHours(properties.getTtl().getHours()));
    } catch (DataAccessException unavailable) {
      response.sendError(
          HttpStatus.SERVICE_UNAVAILABLE.value(), "IDEMPOTENCY_UNAVAILABLE");
      return;
    }

    if (decision.kind() == IdempotencyClaimDecision.Kind.COMPLETED) {
      replay(response, decision.completed());
      return;
    }
    if (decision.kind() == IdempotencyClaimDecision.Kind.HASH_MISMATCH) {
      response.sendError(HttpStatus.UNPROCESSABLE_ENTITY.value(), "IDEMPOTENCY_KEY_REUSED");
      return;
    }
    if (decision.kind() == IdempotencyClaimDecision.Kind.IN_PROGRESS) {
      long retryAfter =
          Math.max(1, Duration.between(java.time.Instant.now(), decision.retryAt()).toSeconds());
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
      response.sendError(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_IN_PROGRESS");
      return;
    }

    IdempotencyClaim claim = decision.claim();
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    try {
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      transaction.execute(
          status -> {
            try {
              durableService.open(claim);
              filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), wrapper);
              int responseStatus = wrapper.getStatus();
              if (responseStatus >= 200 && responseStatus < 300) {
                byte[] responseBytes = wrapper.getContentAsByteArray();
                boolean truncated = responseBytes.length > properties.getResponseMaxBytes();
                String responseBody =
                    truncated
                        ? null
                        : new String(responseBytes, StandardCharsets.UTF_8);
                String reconstructionMetadata =
                    truncated ? reconstructionMetadata(responseBytes) : null;
                durableService.complete(
                    claim,
                    responseStatus,
                    responseBody,
                    truncated,
                    reconstructionMetadata,
                    wrapper.getHeader(HttpHeaders.LOCATION),
                    wrapper.getContentType());
              } else {
                status.setRollbackOnly();
              }
            } catch (IOException | ServletException e) {
              throw new ServletFlowException(e);
            }
            return null;
          });
      if (wrapper.getStatus() < 200 || wrapper.getStatus() >= 300) {
        durableService.deleteIfOwned(claim);
      }
    } catch (ServletFlowException e) {
      durableService.deleteIfOwned(claim);
      Throwable cause = e.getCause();
      if (cause instanceof IOException io) throw io;
      if (cause instanceof ServletException servlet) throw servlet;
      throw e;
    } catch (RuntimeException e) {
      durableService.deleteIfOwned(claim);
      throw e;
    } finally {
      wrapper.copyBodyToResponse();
    }
  }

  private void replay(HttpServletResponse response, IdempotencyRecordSnapshot record)
      throws IOException {
    response.setStatus(record.responseStatus());
    if (record.responseContentType() != null) {
      response.setContentType(record.responseContentType());
    }
    if (record.responseLocation() != null) {
      response.setHeader(HttpHeaders.LOCATION, record.responseLocation());
    }
    response.setHeader("Idempotency-Replayed", "true");
    if (record.responseBody() != null) {
      response.getOutputStream().write(record.responseBody().getBytes(StandardCharsets.UTF_8));
    } else if (record.responseTruncated()) {
      byte[] reconstructed = reconstructAsyncResponse(record);
      response.getOutputStream().write(reconstructed);
    } else {
      response.sendError(
          HttpStatus.INTERNAL_SERVER_ERROR.value(), "Stored response is unavailable");
    }
  }

  private byte[] reconstructAsyncResponse(IdempotencyRecordSnapshot record) throws IOException {
    if (record.responseReconstructionMetadata() != null) {
      return objectMapper.readTree(record.responseReconstructionMetadata()).toString()
          .getBytes(StandardCharsets.UTF_8);
    }
    if (record.responseLocation() == null) {
      throw new IOException("Truncated idempotency response has no reconstruction location");
    }
    String[] pathSegments = record.responseLocation().split("/");
    if (pathSegments.length == 0) {
      throw new IOException("Truncated idempotency response has an invalid location");
    }
    java.util.UUID simulationId;
    try {
      simulationId = java.util.UUID.fromString(pathSegments[pathSegments.length - 2]);
    } catch (IllegalArgumentException ex) {
      throw new IOException("Truncated idempotency response has an invalid simulation location", ex);
    }
    SimulationStatusResponse status =
        simulationPort.getSimulationStatus(simulationId, currentUserId());
    return objectMapper.writeValueAsBytes(status);
  }

  private String reconstructionMetadata(byte[] responseBytes) throws IOException {
    var response = objectMapper.readTree(responseBytes);
    var metadata = objectMapper.createObjectNode();
    response
        .fields()
        .forEachRemaining(
            entry -> {
              if (!"result".equals(entry.getKey())) {
                metadata.set(entry.getKey(), entry.getValue());
              }
            });
    return objectMapper.writeValueAsString(metadata);
  }

  private java.util.UUID currentUserId() throws IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      throw new IOException("Authenticated user is required to reconstruct an idempotency response");
    }
    return principal.id();
  }

  private java.util.UUID scopeId(byte[] body) {
    try {
      String value = objectMapper.readTree(body).path("portfolioId").asText(null);
      return value == null ? UUID_SENTINEL : java.util.UUID.fromString(value);
    } catch (Exception ignored) {
      return UUID_SENTINEL;
    }
  }

  private void legacyRedisFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    String key = request.getHeader("Idempotency-Key");
    if (key == null || key.isBlank()) {
      response.sendError(
          HttpStatus.BAD_REQUEST.value(), "Required header 'Idempotency-Key' is missing.");
      return;
    }
    if (!isValidIdempotencyKey(key)) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid 'Idempotency-Key' format.");
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "User authentication required.");
      return;
    }
    String storageKey = properties.getKeyPrefix() + principal.id() + ":" + key;
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(
                storageKey,
                new IdempotencyRecord(IdempotencyStatus.PENDING, 0, null),
                properties.getTtl().getHours(),
                java.util.concurrent.TimeUnit.HOURS);
    if (Boolean.FALSE.equals(acquired)) {
      IdempotencyRecord existing =
          (IdempotencyRecord) redisTemplate.opsForValue().get(storageKey);
      if (existing != null && existing.status() == IdempotencyStatus.COMPLETED) {
        response.setStatus(existing.statusCode());
        response.setContentType("application/json");
        response.addHeader("X-Cache", "Idempotent-Hit");
        // This is a byte-for-byte replay of a previously generated JSON API response, not an HTML view.
        // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer
        response.getOutputStream().write(existing.responseBody().getBytes(StandardCharsets.UTF_8));
        return;
      }
      response.sendError(
          HttpStatus.CONFLICT.value(), "An identical request is currently processing.");
      return;
    }
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, wrapper);
      if (wrapper.getStatus() >= 200 && wrapper.getStatus() < 300) {
        redisTemplate
            .opsForValue()
            .set(
                storageKey,
                new IdempotencyRecord(
                    IdempotencyStatus.COMPLETED,
                    wrapper.getStatus(),
                    new String(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding())),
                properties.getTtl().getHours(),
                java.util.concurrent.TimeUnit.HOURS);
      } else {
        redisTemplate.delete(storageKey);
      }
    } catch (Exception e) {
      redisTemplate.delete(storageKey);
      throw e;
    } finally {
      wrapper.copyBodyToResponse();
    }
  }

  private String requestHash(String method, String path, byte[] body) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update((method + "\n" + path + "\n").getBytes(StandardCharsets.UTF_8));
      digest.update(body);
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean isValidIdempotencyKey(String key) {
    int length = key.length();
    return length >= properties.getKey().getMinLength()
        && length <= properties.getKey().getMaxLength()
        && Pattern.matches(properties.getKey().getPattern(), key);
  }

  private static class ServletFlowException extends RuntimeException {
    ServletFlowException(Exception cause) { super(cause); }
  }
}
