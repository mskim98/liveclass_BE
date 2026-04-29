package src.alert_system.notification.application.exception;

import lombok.Getter;
import src.alert_system.notification.domain.enums.DeliveryEvent;
import src.alert_system.notification.domain.enums.DeliveryState;

@Getter
public class IllegalDeliveryTransitionException extends RuntimeException {

    private final Long deliveryId;
    private final DeliveryState currentState;
    private final DeliveryEvent event;

    public IllegalDeliveryTransitionException(final Long deliveryId,
                                              final DeliveryState currentState,
                                              final DeliveryEvent event) {
        super("발송단위 상태전이 거부 deliveryId=" + deliveryId
                + ", currentState=" + currentState
                + ", event=" + event);
        this.deliveryId = deliveryId;
        this.currentState = currentState;
        this.event = event;
    }
}
