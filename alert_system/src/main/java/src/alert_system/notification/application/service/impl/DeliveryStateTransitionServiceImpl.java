package src.alert_system.notification.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.DeliveryEvent;
import src.alert_system.notification.domain.enums.DeliveryState;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStateTransitionServiceImpl implements DeliveryStateTransitionService {

    private final StateMachineFactory<DeliveryState, DeliveryEvent> deliveryStateMachineFactory;

    /*
     * 상태머신은 메모리상의 검증 도구일 뿐 영속 상태는 DB 컬럼이 단일 진실 원천(SSoT)
     * 매 호출마다 (1) 인스턴스 생성 → (2) 현재 state 로 rehydrate → (3) event 전송 →
     * (4) 라이브러리가 전이 가능 여부 검증 → (5) 가능 시 entity 메서드 호출 → JPA dirty checking 으로 영속화
     */

    // 상태머신 검증 후 발송단위 상태전이 적용 메서드
    @Override
    public DeliveryState transition(final NotificationDelivery delivery,
                                    final DeliveryTransitionCommand command) {

        final DeliveryState currentState = delivery.getState();
        final StateMachine<DeliveryState, DeliveryEvent> stateMachine = rehydrate(currentState);

        final boolean accepted = sendEvent(stateMachine, command.event());

        if (!accepted) {
            log.warn("[delivery-sm] 상태전이 거부 deliveryId={}, currentState={}, event={}",
                    delivery.getId(), currentState, command.event());

            throw new IllegalDeliveryTransitionException(
                    delivery.getId(), currentState, command.event());
        }

        final DeliveryState newState = stateMachine.getState().getId();
        delivery.transitionTo(newState,
                command.now(),
                command.reason(),
                command.nextAttemptAt(),
                command.externalMessageId());

        log.info("[delivery-sm] 상태전이 완료 deliveryId={}, {} --{}--> {}",
                delivery.getId(), currentState, command.event(), newState);

        return newState;
    }

    // 발송단위 현재상태로 상태머신 인스턴스 복구 메서드
    private StateMachine<DeliveryState, DeliveryEvent> rehydrate(final DeliveryState current) {
        final StateMachine<DeliveryState, DeliveryEvent> stateMachine =
                deliveryStateMachineFactory.getStateMachine();

        stateMachine.stopReactively().block();
        stateMachine.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(current, null, null, null)
                ).block()
        );
        stateMachine.startReactively().block();

        return stateMachine;
    }

    // 상태머신 이벤트 전송 후 수용 여부 판단 메서드
    private boolean sendEvent(final StateMachine<DeliveryState, DeliveryEvent> stateMachine,
                              final DeliveryEvent event) {

        final Message<DeliveryEvent> message = MessageBuilder.withPayload(event).build();
        final List<StateMachineEventResult<DeliveryState, DeliveryEvent>> results =
                stateMachine.sendEvent(Mono.just(message)).collectList().block();

        return results != null
                && !results.isEmpty()
                && results.stream().allMatch(r ->
                        r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED);
    }
}
