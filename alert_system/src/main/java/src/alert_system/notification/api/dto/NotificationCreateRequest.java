package src.alert_system.notification.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NotificationCreateRequest(

        @NotEmpty(message = "receiverIds 는 1명 이상 필요")
        List<@NotNull UUID> receiverIds,

        @NotNull(message = "type 은 필수")
        NotificationType type,

        @Size(max = 50)
        String referenceType,

        @Size(max = 64)
        String referenceId,

        @NotNull(message = "payload 는 필수")
        Map<String, Object> payload,

        @NotEmpty(message = "channels 는 1개 이상 필요")
        List<@NotNull Channel> channels,

        Instant scheduledAt
) {
}
