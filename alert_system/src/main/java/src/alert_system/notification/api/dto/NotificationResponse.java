package src.alert_system.notification.api.dto;

import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NotificationResponse(
        String id,
        String idempotencyKey,
        NotificationType type,
        String referenceType,
        String referenceId,
        Map<String, Object> payload,
        Instant scheduledAt,
        Instant createdAt,
        List<NotificationDeliveryResponse> deliveries
) {
}
