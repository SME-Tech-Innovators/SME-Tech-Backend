package sme.tech.innovators.sme.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sme.tech.innovators.sme.exception.RateLimitExceededException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    @Value("${app.rate-limit.ip-max-attempts:5}")
    private int ipMaxAttempts;

    @Value("${app.rate-limit.email-max-attempts:3}")
    private int emailMaxAttempts;

    @Value("${app.rate-limit.window-minutes:60}")
    private int windowMinutes;

    @Value("${app.rate-limit.block-duration-minutes:60}")
    private int blockDurationMinutes;

    @Value("${app.rate-limit.order-lookup-max-attempts:30}")
    private int orderLookupMaxAttempts;

    private final Cache<String, AtomicInteger> ipAttemptCache;
    private final Cache<String, AtomicInteger> emailAttemptCache;
    private final Cache<String, AtomicInteger> ipConsecutiveViolations;
    private final Cache<String, Boolean> blockedIps;
    private final Cache<String, AtomicInteger> orderLookupAttemptCache;

    public RateLimitService() {
        this.ipAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.emailAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.ipConsecutiveViolations = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.blockedIps = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.orderLookupAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(20000)
                .build();
    }

    public void checkRateLimit(String ipAddress, String email) {
        if (Boolean.TRUE.equals(blockedIps.getIfPresent(ipAddress))) {
            throw new RateLimitExceededException("IP address temporarily blocked", "IP_BLOCKED");
        }

        AtomicInteger ipCount = ipAttemptCache.get(ipAddress, k -> new AtomicInteger(0));
        if (ipCount.get() >= ipMaxAttempts) {
            AtomicInteger violations = ipConsecutiveViolations.get(ipAddress, k -> new AtomicInteger(0));
            if (violations.incrementAndGet() >= 3) {
                blockedIps.put(ipAddress, true);
            }
            throw new RateLimitExceededException("Too many registration attempts from this IP", "IP");
        }

        if (email != null) {
            AtomicInteger emailCount = emailAttemptCache.get(email, k -> new AtomicInteger(0));
            if (emailCount.get() >= emailMaxAttempts) {
                throw new RateLimitExceededException("Too many registration attempts for this email", "EMAIL");
            }
        }
    }

    /**
     * Rate-limit public order lookup (enumeration / abuse). Counts every call per IP.
     */
    public void checkAndIncrementOrderLookup(String ipAddress) {
        String key = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();
        AtomicInteger count = orderLookupAttemptCache.get(key, k -> new AtomicInteger(0));
        int next = count.incrementAndGet();
        int limit = Math.max(orderLookupMaxAttempts, 1);
        if (next > limit) {
            log.warn("Order lookup rate limit exceeded for ip={} attempts={}", key, next);
            throw new RateLimitExceededException(
                    "Too many order lookup attempts. Please try again later.", "ORDER_LOOKUP");
        }
    }

    public void incrementAttempt(String ipAddress, String email) {
        ipAttemptCache.get(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
        if (email != null) {
            emailAttemptCache.get(email, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    public void resetAttempts(String ipAddress, String email) {
        ipAttemptCache.invalidate(ipAddress);
        if (email != null) {
            emailAttemptCache.invalidate(email);
        }
        ipConsecutiveViolations.invalidate(ipAddress);
    }
}
