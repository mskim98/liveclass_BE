package src.alert_system.notification.application.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryEvent;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.infrastructure.statemachine.DeliveryStateMachineConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/*
 * Spring State Machine 빈이 필요하므로 최소 Spring 컨텍스트(DeliveryStateMachineConfig 만) 로드
 * DeliveryStateTransitionServiceImpl 은 팩토리를 주입받아 수동 생성 — DB 의존 없음
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DeliveryStateMachineConfig.class})
class DeliveryStateTransitionServiceImplTest {

    @Autowired
    private StateMachineFactory<DeliveryState, DeliveryEvent> deliveryStateMachineFactory;

    private DeliveryStateTransitionServiceImpl transitionService;

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        transitionService = new DeliveryStateTransitionServiceImpl(deliveryStateMachineFactory);
    }

    // PENDING + DISPATCH → PROCESSING
    @Test
    @DisplayName("PENDING + DISPATCH → PROCESSING 전이 성공")
    void transition_pendingToProcessing_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PENDING);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.dispatch(NOW));

        assertThat(result).isEqualTo(DeliveryState.PROCESSING);
    }

    // PROCESSING + SEND_SUCCESS → SENT
    @Test
    @DisplayName("PROCESSING + SEND_SUCCESS → SENT 전이 성공")
    void transition_processingToSent_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PROCESSING);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.sendSuccess(NOW, "msg-id-001"));

        assertThat(result).isEqualTo(DeliveryState.SENT);
    }

    // PROCESSING + SEND_FAILURE → FAILED
    @Test
    @DisplayName("PROCESSING + SEND_FAILURE → FAILED 전이 성공")
    void transition_processingToFailed_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PROCESSING);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.sendFailure(NOW, "connection timeout", NOW.plusSeconds(60)));

        assertThat(result).isEqualTo(DeliveryState.FAILED);
    }

    // PROCESSING + EXHAUST → DEAD
    @Test
    @DisplayName("PROCESSING + EXHAUST → DEAD 전이 성공")
    void transition_processingToDead_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PROCESSING);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.exhaust(NOW, "max retries exceeded"));

        assertThat(result).isEqualTo(DeliveryState.DEAD);
    }

    // FAILED + RETRY → PROCESSING
    @Test
    @DisplayName("FAILED + RETRY → PROCESSING 전이 성공")
    void transition_failedToProcessing_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.FAILED);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.retry(NOW));

        assertThat(result).isEqualTo(DeliveryState.PROCESSING);
    }

    // PROCESSING + RECOVER → PENDING
    @Test
    @DisplayName("PROCESSING + RECOVER → PENDING 전이 성공 (좀비 복구)")
    void transition_processingToPending_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PROCESSING);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.recover(NOW, NOW.plusSeconds(30)));

        assertThat(result).isEqualTo(DeliveryState.PENDING);
    }

    // DEAD + MANUAL_RETRY → PENDING
    @Test
    @DisplayName("DEAD + MANUAL_RETRY → PENDING 전이 성공 (운영자 수동 재시도)")
    void transition_deadToPending_accepted() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.DEAD);

        final DeliveryState result = transitionService.transition(
                delivery, DeliveryTransitionCommand.manualRetry(NOW, NOW));

        assertThat(result).isEqualTo(DeliveryState.PENDING);
    }

    // SENT(terminal) + 임의 이벤트 → 전이 거부 (IllegalDeliveryTransitionException)
    @Test
    @DisplayName("SENT(terminal) 상태에서 이벤트 전송 시 IllegalDeliveryTransitionException")
    void transition_fromSentTerminal_throwsException() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.SENT);

        assertThatThrownBy(() -> transitionService.transition(
                delivery, DeliveryTransitionCommand.dispatch(NOW)))
                .isInstanceOf(IllegalDeliveryTransitionException.class)
                .satisfies(e -> {
                    final IllegalDeliveryTransitionException ex = (IllegalDeliveryTransitionException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(DeliveryState.SENT);
                    assertThat(ex.getEvent()).isEqualTo(DeliveryEvent.DISPATCH);
                });
    }

    // DEAD + DISPATCH → 전이 거부 (MANUAL_RETRY 외 이벤트 불허)
    @Test
    @DisplayName("DEAD 상태에서 DISPATCH 이벤트 시 IllegalDeliveryTransitionException")
    void transition_deadWithDispatch_throwsException() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.DEAD);

        assertThatThrownBy(() -> transitionService.transition(
                delivery, DeliveryTransitionCommand.dispatch(NOW)))
                .isInstanceOf(IllegalDeliveryTransitionException.class)
                .satisfies(e -> {
                    final IllegalDeliveryTransitionException ex = (IllegalDeliveryTransitionException) e;
                    assertThat(ex.getCurrentState()).isEqualTo(DeliveryState.DEAD);
                    assertThat(ex.getEvent()).isEqualTo(DeliveryEvent.DISPATCH);
                });
    }

    // PENDING + SEND_SUCCESS → 전이 거부 (중간 단계 건너뜀 불허)
    @Test
    @DisplayName("PENDING 에서 SEND_SUCCESS 이벤트 시 IllegalDeliveryTransitionException (단계 건너뜀 불허)")
    void transition_pendingWithSendSuccess_throwsException() {
        final NotificationDelivery delivery = deliveryInState(DeliveryState.PENDING);

        assertThatThrownBy(() -> transitionService.transition(
                delivery, DeliveryTransitionCommand.sendSuccess(NOW, "msg-id")))
                .isInstanceOf(IllegalDeliveryTransitionException.class);
    }

    // 상태머신 rehydrate 가 독립적임을 확인 — 연속 전이에서 서로 오염 없음
    @Test
    @DisplayName("연속 전이 시 각 호출이 독립된 상태머신으로 동작 (상태 오염 없음)")
    void transition_consecutiveCalls_eachIndependent() {
        final NotificationDelivery delivery1 = deliveryInState(DeliveryState.PENDING);
        final NotificationDelivery delivery2 = deliveryInState(DeliveryState.PROCESSING);

        final DeliveryState result1 = transitionService.transition(
                delivery1, DeliveryTransitionCommand.dispatch(NOW));
        final DeliveryState result2 = transitionService.transition(
                delivery2, DeliveryTransitionCommand.sendSuccess(NOW, null));

        assertThat(result1).isEqualTo(DeliveryState.PROCESSING);
        assertThat(result2).isEqualTo(DeliveryState.SENT);
    }

    // 테스트용 — 특정 상태인 NotificationDelivery mock 생성
    private NotificationDelivery deliveryInState(final DeliveryState state) {
        final NotificationDelivery delivery = mock(NotificationDelivery.class);
        given(delivery.getState()).willReturn(state);
        given(delivery.getId()).willReturn(1L);
        return delivery;
    }
}
