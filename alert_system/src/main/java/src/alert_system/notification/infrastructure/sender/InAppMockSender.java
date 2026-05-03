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
public class InAppMockSender implements NotificationSender {

    private final TemplateRenderer templateRenderer;
    private final double failureRate;

    public InAppMockSender(
            final TemplateRenderer templateRenderer,
            @Value("${notification.sender.in-app.failure-rate:0.0}") final double failureRate) {
        this.templateRenderer = templateRenderer;
        this.failureRate = failureRate;
    }

    @Override
    public Channel supports() {
        return Channel.IN_APP;
    }

    // 인앱 푸시 mock 발송 메서드 (DB 적재 가정 — 템플릿 렌더링 후 로그 출력)
    @Override
    public SendResult send(final NotificationDelivery delivery) {
        final Notification notification = delivery.getNotification();
        final RenderedMessage message = templateRenderer.render(
                notification.getType(), Channel.IN_APP, notification.getPayload());

        log.debug("[inapp-sender] 렌더링 완료 deliveryId={} title='{}' body='{}'",
                delivery.getId(), message.title(), message.body());

        final double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < failureRate) {
            log.warn("[inapp-sender] 일시 실패 주입 deliveryId={}", delivery.getId());
            return SendResult.retryable(DeliveryErrorCode.EXTERNAL_SERVICE_ERROR, "mock inapp store unavailable");
        }

        log.info("[inapp-sender] 발송 성공 deliveryId={} title='{}' body='{}'",
                delivery.getId(), message.title(), message.body());
        return SendResult.success("inapp-mock-" + delivery.getDispatchKey());
    }
}
