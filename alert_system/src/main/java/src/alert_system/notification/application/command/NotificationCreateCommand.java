package src.alert_system.notification.application.command;

import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NotificationCreateCommand(
        List<UUID> receiverIds,
        NotificationType type,
        String referenceType,
        String referenceId,
        Map<String, Object> payload,
        List<Channel> channels,
        Instant scheduledAt
) {
}
