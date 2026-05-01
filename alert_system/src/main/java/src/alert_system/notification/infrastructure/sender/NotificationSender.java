package src.alert_system.notification.infrastructure.sender;

import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;

public interface NotificationSender {

    // SenderRouter 가 supports() 를 보고 Map<Channel, Sender> 구성
    Channel supports();

    // 외부 시스템(이메일 provider, 인앱 푸시 등) 으로 알림 발송
    SendResult send(NotificationDelivery delivery);
}
