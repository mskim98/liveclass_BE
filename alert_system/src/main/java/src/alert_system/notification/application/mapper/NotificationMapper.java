package src.alert_system.notification.application.mapper;

import org.springframework.stereotype.Component;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.domain.entities.Notification;

@Component
public class NotificationMapper {

    // 알림 엔티티 생성 메서드 (멱등키는 도메인이 derive 한 값을 외부에서 주입받음)
    public Notification toEntity(final NotificationCreateCommand command, final String idempotencyKey) {
        return Notification.builder()
                .idempotencyKey(idempotencyKey)
                .type(command.type())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .payload(command.payload())
                .scheduledAt(command.scheduledAt())
                .build();
    }
}
