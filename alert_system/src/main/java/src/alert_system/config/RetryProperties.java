package src.alert_system.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.retry")
public record RetryProperties(

        // 백오프 시작 (초) — 첫 재시도 대기 시간
        @Min(1) long baseDelaySeconds,

        // 지수 인자 (delay = base × factor^retryCount)
        @DecimalMin("1.0") double multiplier,

        // 백오프 상한 (초) — 너무 길어지지 않게 cap
        @Min(1) long maxDelaySeconds,

        // jitter 비율 (0.0~1.0) — 동시 재시도 thundering herd 방지
        @DecimalMin("0.0") @DecimalMax("1.0") double jitterRatio
) {
}
