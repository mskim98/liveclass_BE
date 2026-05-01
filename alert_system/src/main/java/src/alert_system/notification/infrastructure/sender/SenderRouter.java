package src.alert_system.notification.infrastructure.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SenderRouter {

    private final Map<Channel, NotificationSender> senders;

    public SenderRouter(final List<NotificationSender> senderList) {
        final Map<Channel, NotificationSender> indexed = new EnumMap<>(Channel.class);
        for (final NotificationSender sender : senderList) {
            final NotificationSender duplicate = indexed.put(sender.supports(), sender);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "동일 채널에 대해 두 개 이상의 Sender 등록됨 channel=" + sender.supports()
                                + " existing=" + duplicate.getClass().getSimpleName()
                                + " new=" + sender.getClass().getSimpleName());
            }
        }
        this.senders = Map.copyOf(indexed);
        log.info("[sender-router] registered channels={}", this.senders.keySet());
    }

    // 채널에 매핑된 sender 로 발송 위임 메서드
    public SendResult send(final NotificationDelivery delivery) {
        final NotificationSender sender = senders.get(delivery.getChannel());
        if (sender == null) {

            return SendResult.nonRetryable(
                    src.alert_system.notification.domain.enums.DeliveryErrorCode.UNKNOWN,
                    "no sender registered for channel " + delivery.getChannel());
        }
        return sender.send(delivery);
    }
}
