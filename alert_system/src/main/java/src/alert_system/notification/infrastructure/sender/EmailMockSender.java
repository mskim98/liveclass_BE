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
public class EmailMockSender implements NotificationSender {
    
    private final double failureRate;
    private final double nonRetryableRate;

    public EmailMockSender(
            @Value("${notification.sender.email.failure-rate:0.0}") final double failureRate,
            @Value("${notification.sender.email.non-retryable-rate:0.0}") final double nonRetryableRate) {
        this.failureRate = failureRate;
        this.nonRetryableRate = nonRetryableRate;
    }

    @Override
    public Channel supports() {
        return Channel.EMAIL;
    }

    // 이메일 mock 발송 메서드
    @Override
    public SendResult send(final NotificationDelivery delivery) {
        final double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < nonRetryableRate) {
            log.warn("[email-sender] 비재시도성 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.nonRetryable(DeliveryErrorCode.INVALID_RECIPIENT, "mock invalid recipient");
        }

        if (roll < nonRetryableRate + failureRate) {
            log.warn("[email-sender] 일시 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.retryable(DeliveryErrorCode.NETWORK_ERROR, "mock transient network error");
        }

        log.debug("[email-sender] 발송 성공 deliveryId={} dispatchKey={}",
                delivery.getId(), delivery.getDispatchKey());
        return SendResult.success("email-mock-" + delivery.getDispatchKey());
    }
}
