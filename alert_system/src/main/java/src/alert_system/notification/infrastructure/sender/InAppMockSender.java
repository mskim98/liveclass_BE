package src.alert_system.notification.infrastructure.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class InAppMockSender implements NotificationSender {

    private final double failureRate;

    public InAppMockSender(
            @Value("${notification.sender.in-app.failure-rate:0.0}") final double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public Channel supports() {
        return Channel.IN_APP;
    }

    // 인앱 푸시 mock 발송 메서드 (DB 적재 가정 — 비재시도성 실패 시나리오는 거의 없음)
    @Override
    public SendResult send(final NotificationDelivery delivery) {
        final double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < failureRate) {
            log.warn("[inapp-sender] 일시 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.retryable(DeliveryErrorCode.EXTERNAL_SERVICE_ERROR, "mock inapp store unavailable");
        }

        log.debug("[inapp-sender] 발송 성공 deliveryId={} dispatchKey={}",
                delivery.getId(), delivery.getDispatchKey());
        return SendResult.success("inapp-mock-" + delivery.getDispatchKey());
    }
}
