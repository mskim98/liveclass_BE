package src.alert_system.notification.api.dto;

import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryResponse(
        Long id,
        UUID receiverId,
        Channel channel,
        DeliveryState state,
        int retryCount,
        int maxRetries,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant readAt
) {
}
