package src.alert_system.notification.application.retry;

import java.time.Instant;

public interface RetryPolicy {

    //다음 재시도 시각 계산
    Instant nextAttempt(int retryCount, Instant now);
}
