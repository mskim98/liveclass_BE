package src.alert_system.notification.application.service.spec;

import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.api.dto.UserNotificationResponse;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.domain.enums.Channel;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse createNotification(NotificationCreateCommand command);

    NotificationResponse findById(String notificationId);

    List<UserNotificationResponse> findUserNotifications(UUID userId, Channel channel, Boolean readFilter);

    void markRead(UUID userId, Long deliveryId);
    
    void retryDeadDelivery(String notificationId, Long deliveryId);
}
