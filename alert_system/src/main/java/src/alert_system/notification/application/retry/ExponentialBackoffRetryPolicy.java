package src.alert_system.notification.application.retry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import src.alert_system.config.RetryProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final RetryProperties properties;

    // 다음 재시도 시각 계산 메서드
    @Override
    public Instant nextAttempt(final int retryCount, final Instant now) {
        final double exponent = Math.pow(properties.multiplier(), retryCount);
        final long rawDelaySec = (long) Math.min(
                properties.baseDelaySeconds() * exponent,
                properties.maxDelaySeconds());

        final long jitterMs = computeJitterMs(rawDelaySec);

        return now.plus(Duration.ofSeconds(rawDelaySec)).plusMillis(jitterMs);
    }

    // jitter 계산 메서드 (지정 비율 범위 내 랜덤 ±)
    private long computeJitterMs(final long delaySec) {
        if (properties.jitterRatio() <= 0.0) {
            return 0L;
        }
        final long jitterRangeMs = (long) (delaySec * 1000 * properties.jitterRatio());
        if (jitterRangeMs == 0L) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(-jitterRangeMs, jitterRangeMs + 1);
    }
}
