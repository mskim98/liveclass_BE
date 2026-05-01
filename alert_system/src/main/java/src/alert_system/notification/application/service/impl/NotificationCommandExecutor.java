package src.alert_system.notification.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.application.mapper.NotificationDeliveryMapper;
import src.alert_system.notification.application.mapper.NotificationMapper;
import src.alert_system.notification.application.mapper.NotificationResponseMapper;
import src.alert_system.notification.domain.entities.Notification;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.infrastructure.persistence.NotificationRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class NotificationCommandExecutor {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;
    private final NotificationResponseMapper notificationResponseMapper;

    // 멱등키로 기존 알림 조회 메서드 (각 호출이 독립 트랜잭션 — race 복구 시 fresh PG 트랜잭션 보장)
    @Transactional(readOnly = true)
    public Optional<NotificationResponse> findByIdempotencyKey(final String idempotencyKey) {
        return notificationRepository.findByIdempotencyKeyWithDeliveries(idempotencyKey)
                .map(notificationResponseMapper::toResponse);
    }

    // 알림 단건 조회 메서드
    @Transactional(readOnly = true)
    public NotificationResponse findById(final String notificationId) {
        final Notification notification = notificationRepository.findByIdWithDeliveries(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        return notificationResponseMapper.toResponse(notification);
    }

    // 신규 알림 영속화 메서드 (UNIQUE 위반 시 DataIntegrityViolationException 그대로 propagate — 트랜잭션 롤백)
    @Transactional
    public NotificationResponse insertNotification(final NotificationCreateCommand command,
                                                   final String idempotencyKey) {
        final Notification notification = notificationMapper.toEntity(command, idempotencyKey);
        final List<NotificationDelivery> deliveries = notificationDeliveryMapper.toInitialDeliveries(
                command.receiverIds(), command.channels(), command.scheduledAt());
        deliveries.forEach(notification::addDelivery);

        final Notification saved = notificationRepository.saveAndFlush(notification);
        return notificationResponseMapper.toResponse(saved);
    }
}
