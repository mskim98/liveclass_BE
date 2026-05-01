package src.alert_system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties({WorkerProperties.class, RetryProperties.class, RecoveryProperties.class})
@RequiredArgsConstructor
public class AsyncConfig {

    public static final String NOTIFICATION_DISPATCH_EXECUTOR = "notificationDispatchExecutor";

    private final WorkerProperties workerProperties;

    // 알림 발송 비동기 실행기 등록 메서드 (큐 가득 차면 호출자 스레드가 직접 실행 — 자연 백프레셔)
    @Bean(name = NOTIFICATION_DISPATCH_EXECUTOR)
    public ThreadPoolTaskExecutor notificationDispatchExecutor() {
        final WorkerProperties.ThreadPool config = workerProperties.threadPool();

        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.coreSize());
        executor.setMaxPoolSize(config.maxSize());
        executor.setQueueCapacity(config.queueCapacity());
        executor.setThreadNamePrefix(config.threadNamePrefix());

        // CallerRunsPolicy : 큐 + 풀 모두 포화 시 폴링 스레드가 직접 작업 실행
        // → 작업 손실 0, 폴링 속도가 시스템 처리량에 자동 적응 (백프레셔)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // graceful shutdown — 진행 중 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("[notification] dispatch executor initialized core={} max={} queue={}",
                config.coreSize(), config.maxSize(), config.queueCapacity());
        return executor;
    }
}
