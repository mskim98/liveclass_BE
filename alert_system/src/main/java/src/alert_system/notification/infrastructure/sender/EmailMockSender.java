package src.alert_system.notification.infrastructure.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.entities.Notification;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;
import src.alert_system.notification.infrastructure.template.RenderedMessage;
import src.alert_system.notification.infrastructure.template.TemplateRenderer;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class EmailMockSender implements NotificationSender {

    private final TemplateRenderer templateRenderer;
    private final double failureRate;
    private final double nonRetryableRate;

    public EmailMockSender(
            final TemplateRenderer templateRenderer,
            @Value("${notification.sender.email.failure-rate:0.0}") final double failureRate,
            @Value("${notification.sender.email.non-retryable-rate:0.0}") final double nonRetryableRate) {
        this.templateRenderer = templateRenderer;
        this.failureRate = failureRate;
        this.nonRetryableRate = nonRetryableRate;
    }

    @Override
    public Channel supports() {
        return Channel.EMAIL;
    }

    // 이메일 mock 발송 메서드 (템플릿 렌더링 후 로그 출력)
    @Override
    public SendResult send(final NotificationDelivery delivery) {
        final Notification notification = delivery.getNotification();
        final RenderedMessage message = templateRenderer.render(
                notification.getType(), Channel.EMAIL, notification.getPayload());

        log.debug("[email-sender] 렌더링 완료 deliveryId={} title='{}' body='{}'",
                delivery.getId(), message.title(), message.body());

        final double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < nonRetryableRate) {
            log.warn("[email-sender] 비재시도성 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.nonRetryable(DeliveryErrorCode.INVALID_RECIPIENT, "mock invalid recipient");
        }

        if (roll < nonRetryableRate + failureRate) {
            log.warn("[email-sender] 일시 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.retryable(DeliveryErrorCode.NETWORK_ERROR, "mock transient network error");
        }

        log.info("[email-sender] 발송 성공 deliveryId={} title='{}' body='{}'",
                delivery.getId(), message.title(), message.body());
        return SendResult.success("email-mock-" + delivery.getDispatchKey());
    }
}
