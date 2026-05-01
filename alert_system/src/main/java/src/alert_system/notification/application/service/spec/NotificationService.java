package src.alert_system.notification.application.service.spec;

import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.application.command.NotificationCreateCommand;

public interface NotificationService {

    NotificationResponse createNotification(NotificationCreateCommand command);

    NotificationResponse findById(String notificationId);
}
