package src.alert_system.notification.application.mapper;

import org.springframework.stereotype.Component;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.domain.entities.Notification;

@Component
public class NotificationMapper {

    // 알림 엔티티 생성 메서드 (수신자는 Delivery 단위로 풀어서 보관)
    public Notification toEntity(final NotificationCreateCommand command) {
        return Notification.builder()
                .idempotencyKey(command.idempotencyKey())
                .type(command.type())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .payload(command.payload())
                .scheduledAt(command.scheduledAt())
                .build();
    }
}
