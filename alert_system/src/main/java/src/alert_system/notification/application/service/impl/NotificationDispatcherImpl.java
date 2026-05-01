package src.alert_system.notification.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.application.retry.RetryPolicy;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.application.service.spec.NotificationDispatcher;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.entities.NotificationDeliveryFail;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;
import src.alert_system.notification.infrastructure.observability.NotificationMetrics;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;
import src.alert_system.notification.infrastructure.sender.SendResult;
import src.alert_system.notification.infrastructure.sender.SenderRouter;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private static final String MDC_DELIVERY_ID = "deliveryId";

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryStateTransitionService transitionService;
    private final SenderRouter senderRouter;
    private final RetryPolicy retryPolicy;
    private final NotificationMetrics metrics;

    // 단일 발송단위 디스패치 메서드
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(final Long deliveryId) {
        MDC.put(MDC_DELIVERY_ID, String.valueOf(deliveryId));
        try {
            final NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

            try {
                final SendResult result = senderRouter.send(delivery);
                applyResult(delivery, result);

            } catch (IllegalDeliveryTransitionException ex) {
                // 상태머신 거부 — 이미 다른 워커가 처리 완료한 row 등 (정상 상황 가능)
                log.warn("[dispatcher] 상태전이 거부 state={} reason={}",
                        delivery.getState(), ex.getMessage());
            } catch (Exception ex) {
                // 트랜잭션 롤백 → 좀비 복구 Scheduler 가 PROCESSING → PENDING 복구
                log.error("[dispatcher] 디스패치 실패", ex);
                throw ex;
            }
        } finally {
            MDC.remove(MDC_DELIVERY_ID);
        }
    }

    // 발송 결과를 상태전이 + 실패 이력 + 메트릭에 반영하는 메서드
    private void applyResult(final NotificationDelivery delivery, final SendResult result) {
        final Instant now = Instant.now();

        switch (result) {
            case SendResult.Success success -> {
                transitionService.transition(delivery,
                        DeliveryTransitionCommand.sendSuccess(now, success.externalMessageId()));
                metrics.recordSuccess(delivery.getChannel());
            }

            case SendResult.RetryableFailure failure -> {
                recordFailure(delivery, failure.errorCode(), failure.reason());
                metrics.recordRetryableFailure(delivery.getChannel(), failure.errorCode());
                applyRetryableFailure(delivery, failure.reason(), now);
            }

            case SendResult.NonRetryableFailure failure -> {
                recordFailure(delivery, failure.errorCode(), failure.reason());
                metrics.recordNonRetryableFailure(delivery.getChannel(), failure.errorCode());
                transitionService.transition(delivery,
                        DeliveryTransitionCommand.exhaust(now, failure.reason()));
                metrics.recordExhausted(delivery.getChannel());
            }
        }
    }

    // 재시도 가능 실패 분기 처리 메서드 (canRetry 에 따라 sendFailure 또는 exhaust)
    private void applyRetryableFailure(final NotificationDelivery delivery,
                                       final String reason,
                                       final Instant now) {
        if (delivery.canRetry()) {
            final Instant nextAttempt = retryPolicy.nextAttempt(delivery.getRetryCount(), now);
            transitionService.transition(delivery,
                    DeliveryTransitionCommand.sendFailure(now, reason, nextAttempt));
        } else {
            transitionService.transition(delivery,
                    DeliveryTransitionCommand.exhaust(now, reason));
            metrics.recordExhausted(delivery.getChannel());
        }
    }

    // 실패 이력 적재 메서드 (cascade ALL — delivery 저장 시 동반 INSERT)
    private void recordFailure(final NotificationDelivery delivery,
                               final DeliveryErrorCode errorCode,
                               final String reason) {
        final NotificationDeliveryFail failure = NotificationDeliveryFail.builder()
                .errorCode(errorCode)
                .errorMessage(reason)
                .build();
        delivery.recordFailure(failure);
    }
}
