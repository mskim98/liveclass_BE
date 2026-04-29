package src.alert_system.notification.api.mapper;

import org.springframework.stereotype.Component;
import src.alert_system.notification.api.dto.NotificationCreateRequest;
import src.alert_system.notification.application.command.NotificationCreateCommand;

@Component
public class NotificationRequestMapper {

    // API 요청 DTO 를 application command 로 변환 메서드
    public NotificationCreateCommand toCommand(final NotificationCreateRequest request) {
        return new NotificationCreateCommand(
                request.receiverIds(),
                request.type(),
                request.referenceType(),
                request.referenceId(),
                request.payload(),
                request.channels(),
                request.scheduledAt()
        );
    }
}
