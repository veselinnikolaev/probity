package me.veselin.probity.bff.security.interceptor;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.bff.security.rate_limit.RateLimit;
import me.veselin.probity.bff.security.rate_limit.store.BucketStore;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final BucketStore bucketStore;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod method)) return true;

        RateLimit annotation = method.getMethodAnnotation(RateLimit.class);
        if (annotation == null) return true;

        String key = request.getRemoteAddr()
                + ":" + method.getBeanType().getSimpleName()
                + ":" + method.getMethod().getName();

        Bucket bucket = bucketStore.getOrCreate(key, annotation);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Limit", String.valueOf(annotation.requests()));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.addHeader("X-RateLimit-Retry-After", String.valueOf(retryAfter));
        response.setStatus(429);
        response.setContentType("text/plain");
        response.getWriter().write("Rate limit exceeded for this endpoint");
        return false;
    }
}
