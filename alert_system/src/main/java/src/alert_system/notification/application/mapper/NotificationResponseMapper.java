package src.alert_system.notification.application.mapper;

import org.springframework.stereotype.Component;
import src.alert_system.notification.api.dto.NotificationDeliveryResponse;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.domain.entities.Notification;
import src.alert_system.notification.domain.entities.NotificationDelivery;

import java.util.List;

@Component
public class NotificationResponseMapper {

    // 알림 엔티티를 응답 DTO 로 변환 메서드
    public NotificationResponse toResponse(final Notification notification) {
        final List<NotificationDeliveryResponse> deliveryResponses = notification.getDeliveries().stream()
                .map(this::toDeliveryResponse)
                .toList();

        return new NotificationResponse(
                notification.getId(),
                notification.getIdempotencyKey(),
                notification.getType(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getPayload(),
                notification.getScheduledAt(),
                notification.getCreatedAt(),
                deliveryResponses
        );
    }

    // 발송단위 엔티티를 응답 DTO 로 변환 메서드
    private NotificationDeliveryResponse toDeliveryResponse(final NotificationDelivery delivery) {
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getReceiverId(),
                delivery.getChannel(),
                delivery.getState(),
                delivery.getRetryCount(),
                delivery.getMaxRetries(),
                delivery.getNextAttemptAt(),
                delivery.getSentAt(),
                delivery.getReadAt()
        );
    }
}
