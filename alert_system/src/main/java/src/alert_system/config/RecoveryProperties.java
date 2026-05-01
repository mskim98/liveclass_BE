package src.alert_system.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.recovery")
public record RecoveryProperties(

        // 복구 폴링 주기 (ms) — 좀비 검사 간격
        @Min(1000) long pollingIntervalMs,

        // PROCESSING 임계 시간 (ms) — 이 시간 초과 PROCESSING 은 좀비로 간주
        @Min(1000) long stuckThresholdMs,

        // 한 번 복구에서 처리할 최대 row 수
        @Min(1) int batchSize
) {
}
