package src.alert_system.notification.application.mapper;

import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationDeliveryMapper {

    private static final int DEFAULT_MAX_RETRIES = 5;

    // 수신자 × 채널 fan-out 으로 초기 PENDING 발송단위 다중 생성 메서드 (Notification.addDelivery 에서 양방향 연관관계 설정)
    public List<NotificationDelivery> toInitialDeliveries(final List<UUID> receiverIds,
                                                          final List<Channel> channels,
                                                          final Instant scheduledAt) {
        final Instant nextAttemptAt = (scheduledAt != null) ? scheduledAt : Instant.now();

        return receiverIds.stream()
                .flatMap(receiverId -> channels.stream()
                        .map(channel -> NotificationDelivery.builder()
                                .receiverId(receiverId)
                                .channel(channel)
                                .maxRetries(DEFAULT_MAX_RETRIES)
                                .nextAttemptAt(nextAttemptAt)
                                .build()))
                .toList();
    }
}
