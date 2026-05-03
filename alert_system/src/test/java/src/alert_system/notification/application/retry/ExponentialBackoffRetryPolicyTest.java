package src.alert_system.notification.application.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import src.alert_system.config.RetryProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryPolicyTest {

    private static final long BASE_DELAY = 10L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_DELAY = 300L;
    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    private ExponentialBackoffRetryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ExponentialBackoffRetryPolicy(
                new RetryProperties(BASE_DELAY, MULTIPLIER, MAX_DELAY, 0.0));
    }

    // retryCount=0 → base * 2^0 = 10s
    @Test
    @DisplayName("retryCount=0 시 baseDelay(10s) 그대로 적용")
    void nextAttempt_firstRetry_returnsBaseDelay() {
        assertThat(policy.nextAttempt(0, NOW)).isEqualTo(NOW.plusSeconds(10));
    }

    // retryCount 1, 2, 3 → 20s, 40s, 80s
    @Test
    @DisplayName("retryCount 증가 시 지수적으로 delay 증가")
    void nextAttempt_exponentialGrowth() {
        assertThat(policy.nextAttempt(1, NOW)).isEqualTo(NOW.plusSeconds(20));
        assertThat(policy.nextAttempt(2, NOW)).isEqualTo(NOW.plusSeconds(40));
        assertThat(policy.nextAttempt(3, NOW)).isEqualTo(NOW.plusSeconds(80));
    }

    // 10 * 2^10 = 10240s > maxDelay=300s → 300s 로 cap
    @Test
    @DisplayName("계산된 delay 가 maxDelay 초과 시 maxDelay(300s) 로 cap")
    void nextAttempt_cappedAtMaxDelay() {
        assertThat(policy.nextAttempt(10, NOW)).isEqualTo(NOW.plusSeconds(300));
    }

    // jitterRatio=0 이면 같은 입력에 항상 동일한 값 반환
    @Test
    @DisplayName("jitterRatio=0 이면 항상 결정론적 delay 반환")
    void nextAttempt_noJitter_deterministicResult() {
        for (int i = 0; i < 10; i++) {
            assertThat(policy.nextAttempt(0, NOW)).isEqualTo(NOW.plusSeconds(BASE_DELAY));
        }
    }

    // baseDelay=10s, jitter=0.2 → ±2000ms → 결과 [8s, 12s] 범위
    @Test
    @DisplayName("jitterRatio=0.2 이면 ±20% 범위 내 시각 반환")
    void nextAttempt_withJitter_returnsWithinRange() {
        final ExponentialBackoffRetryPolicy jitterPolicy = new ExponentialBackoffRetryPolicy(
                new RetryProperties(BASE_DELAY, MULTIPLIER, MAX_DELAY, 0.2));

        for (int i = 0; i < 30; i++) {
            final Instant result = jitterPolicy.nextAttempt(0, NOW);
            assertThat(result)
                    .isAfterOrEqualTo(NOW.plusSeconds(8))
                    .isBeforeOrEqualTo(NOW.plusSeconds(12));
        }
    }

    // multiplier=1.0 → 지수 없이 baseDelay 고정
    @Test
    @DisplayName("multiplier=1.0 이면 retryCount 무관하게 baseDelay 고정")
    void nextAttempt_multiplierOne_alwaysBaseDelay() {
        final ExponentialBackoffRetryPolicy fixedPolicy = new ExponentialBackoffRetryPolicy(
                new RetryProperties(BASE_DELAY, 1.0, MAX_DELAY, 0.0));

        for (int retryCount = 0; retryCount < 5; retryCount++) {
            assertThat(fixedPolicy.nextAttempt(retryCount, NOW)).isEqualTo(NOW.plusSeconds(BASE_DELAY));
        }
    }
}