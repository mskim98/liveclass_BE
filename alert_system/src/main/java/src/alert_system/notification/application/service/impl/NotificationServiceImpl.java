package src.alert_system.notification.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.api.dto.UserNotificationResponse;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.application.mapper.NotificationResponseMapper;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.application.service.spec.NotificationService;
import src.alert_system.notification.domain.entities.Notification;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationCommandExecutor executor;
    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryStateTransitionService transitionService;
    private final NotificationResponseMapper responseMapper;

    // 알림 등록 메서드
    @Override
    public NotificationResponse createNotification(final NotificationCreateCommand command) {
        // 도메인 정체성으로부터 멱등키 파생
        final String idempotencyKey = Notification.deriveIdempotencyKey(
                command.type(),
                command.referenceType(),
                command.referenceId(),
                command.receiverIds(),
                command.channels());

        // 1단계 — 사전조회로 일반 케이스 빠르게 차단
        final Optional<NotificationResponse> existing = executor.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("멱등키 사전조회 적중 idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        // 신규 INSERT 시도
        try {
            return executor.insertNotification(command, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            // 2단계 — 사전조회와 INSERT 사이 race condition 방어, UNIQUE 위반 시 새 트랜잭션으로 재조회
            log.info("멱등키 race 감지, 기존 row 반환 idempotencyKey={}", idempotencyKey);
            return executor.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, ex));
        }
    }

    // 알림 단건 조회 메서드
    @Override
    public NotificationResponse findById(final String notificationId) {
        return executor.findById(notificationId);
    }

    // 사용자 본인 알림 목록 조회 메서드 (인증 미구현 — userId 는 헤더 또는 path 로 전달)
    @Override
    @Transactional(readOnly = true)
    public List<UserNotificationResponse> findUserNotifications(final UUID userId,
                                                                final Channel channel,
                                                                final Boolean readFilter) {
        return deliveryRepository.findUserDeliveries(userId, channel, readFilter).stream()
                .map(responseMapper::toUserResponse)
                .toList();
    }

    // 인앱 알림 읽음 처리 메서드 (멱등 — 이미 읽음이면 무시)
    @Override
    @Transactional
    public void markRead(final UUID userId, final Long deliveryId) {
        final NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // 소유권 검증 — userId 와 delivery.receiverId 일치해야 함 (인증 simulate)
        if (!delivery.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        // markRead 는 set-once 라 두 번째 호출은 무시됨 (자연 멱등)
        delivery.markRead(Instant.now());
    }

    // 운영자 수동 재시도 메서드 (DEAD → PENDING 으로 되돌림 + retryCount 초기화)
    @Override
    @Transactional
    public void retryDeadDelivery(final String notificationId, final Long deliveryId) {
        final NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // URL 의 notificationId 와 delivery 의 부모 notification 일치 검증 (잘못된 경로 차단)
        if (!delivery.getNotification().getId().equals(notificationId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        // retryCount 초기화 — 이후 RetryPolicy 가 처음부터 백오프 사이클 적용
        delivery.resetForManualRetry();

        final Instant now = Instant.now();
        // 상태머신이 DEAD 가 아닌 경우 IllegalDeliveryTransitionException 발생 (의도된 fail-fast)
        transitionService.transition(delivery,
                DeliveryTransitionCommand.manualRetry(now, now));

        log.info("[manual-retry] DEAD → PENDING 전환 deliveryId={} notificationId={}",
                deliveryId, notificationId);
    }
}
