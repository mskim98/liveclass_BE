package src.alert_system.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.worker")
public record WorkerProperties(

        // 폴링 주기 (ms) — 이전 폴링 완료 후 대기 시간
        @Min(100) long pollingIntervalMs,

        // 한 번 폴링에서 락 잡을 최대 row 수
        @Min(1) int batchSize,

        ThreadPool threadPool
) {

    public record ThreadPool(

            @Min(1) int coreSize,

            @Min(1) int maxSize,

            @Min(0) int queueCapacity,

            @NotBlank String threadNamePrefix
    ) {
    }
}
