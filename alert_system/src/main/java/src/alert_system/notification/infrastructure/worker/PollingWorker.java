package src.alert_system.notification.infrastructure.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import src.alert_system.config.AsyncConfig;
import src.alert_system.config.WorkerProperties;
import src.alert_system.notification.application.service.spec.NotificationDispatcher;

import java.util.List;

@Slf4j
@Component
public class PollingWorker {

    private final DeliveryLockService lockService;
    private final NotificationDispatcher dispatcher;
    private final ThreadPoolTaskExecutor dispatchExecutor;
    private final WorkerProperties workerProperties;

    public PollingWorker(final DeliveryLockService lockService,
                         final NotificationDispatcher dispatcher,
                         @Qualifier(AsyncConfig.NOTIFICATION_DISPATCH_EXECUTOR)
                         final ThreadPoolTaskExecutor dispatchExecutor,
                         final WorkerProperties workerProperties) {
        this.lockService = lockService;
        this.dispatcher = dispatcher;
        this.dispatchExecutor = dispatchExecutor;
        this.workerProperties = workerProperties;
    }

    // 발송 대기 row 폴링 후 ThreadPool 비동기 디스패치 메서드
    @Scheduled(fixedDelayString = "${notification.worker.polling-interval-ms}")
    public void poll() {
        final List<Long> deliveryIds = lockService.lockAndMarkProcessing(workerProperties.batchSize());

        if (deliveryIds.isEmpty()) {
            return;
        }

        log.debug("[polling] locked count={}", deliveryIds.size());

        // CallerRunsPolicy 설정으로 큐 포화 시 폴링 스레드가 직접 실행 → 자연 백프레셔
        // → 다음 polling 호출이 늦어지면서 입력 burst 흡수
        deliveryIds.forEach(id -> dispatchExecutor.execute(() -> safeDispatch(id)));
    }

    // ThreadPool 작업 외부로 예외 누출 차단 메서드 (한 건 실패가 워커 자체를 죽이지 않게)
    private void safeDispatch(final Long deliveryId) {
        try {
            dispatcher.dispatch(deliveryId);
        } catch (Exception ex) {
            log.error("[polling] dispatch 실패 deliveryId={}", deliveryId, ex);
        }
    }
}
