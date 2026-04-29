package src.alert_system.notification.application.service.spec;

import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.DeliveryState;

public interface DeliveryStateTransitionService {

    /**
     * 상태머신 검증 후 발송단위 상태전이 적용
     *
     * @param delivery 영속 상태의 NotificationDelivery
     * @param command  적용할 이벤트와 메타데이터
     * @return 전이 후 새 상태
     * @throws IllegalDeliveryTransitionException 라이브러리가 전이를 거부한 경우
     */
    DeliveryState transition(NotificationDelivery delivery, DeliveryTransitionCommand command);
}
