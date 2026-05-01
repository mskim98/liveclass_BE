package src.alert_system.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import src.alert_system.config.RecoveryProperties;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.infrastructure.observability.NotificationMetrics;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryWorker {

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryStateTransitionService transitionService;
    private final RecoveryProperties recoveryProperties;
    private final NotificationMetrics metrics;

    // 좀비 PROCESSING 복구 메서드
    @Scheduled(fixedDelayString = "${notification.recovery.polling-interval-ms}")
    @Transactional
    public void recover() {
        final Instant now = Instant.now();
        final Instant threshold = now.minus(Duration.ofMillis(recoveryProperties.stuckThresholdMs()));

        final List<NotificationDelivery> stuck =
                deliveryRepository.lockStuckProcessing(threshold, recoveryProperties.batchSize());

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("[recovery] 좀비 PROCESSING 감지 count={} threshold={}", stuck.size(), threshold);

        for (final NotificationDelivery delivery : stuck) {
            try {
                transitionService.transition(delivery,
                        DeliveryTransitionCommand.recover(now, now));
                metrics.recordRecovery();
                log.info("[recovery] 복구 완료 deliveryId={}", delivery.getId());
            } catch (IllegalDeliveryTransitionException ex) {
                // 락 잡는 사이 dispatcher 가 SENT/FAILED 로 전이 완료한 경우 — 정상 상황
                log.debug("[recovery] 복구 불필요 (이미 전이됨) deliveryId={} state={}",
                        delivery.getId(), delivery.getState());
            }
        }
    }
}
