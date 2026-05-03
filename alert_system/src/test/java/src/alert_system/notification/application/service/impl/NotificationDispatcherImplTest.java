package src.alert_system.notification.application.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.exception.IllegalDeliveryTransitionException;
import src.alert_system.notification.application.retry.RetryPolicy;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;
import src.alert_system.notification.domain.enums.DeliveryEvent;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.infrastructure.observability.NotificationMetrics;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;
import src.alert_system.notification.infrastructure.sender.SendResult;
import src.alert_system.notification.infrastructure.sender.SenderRouter;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherImplTest {

    @InjectMocks
    private NotificationDispatcherImpl dispatcher;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private DeliveryStateTransitionService transitionService;

    @Mock
    private SenderRouter senderRouter;

    @Mock
    private RetryPolicy retryPolicy;

    @Mock
    private NotificationMetrics metrics;

    // ─────────────────────────────────────────────────────────
    // 성공 시나리오
    // ─────────────────────────────────────────────────────────

    // 발송 성공 → PROCESSING → SENT 전이 + 성공 메트릭
    @Test
    @DisplayName("발송 성공 시 SEND_SUCCESS 전이 명령 + 성공 메트릭 기록")
    void dispatch_sendSuccess_transitionsToSent() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, true, 0);
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery)).willReturn(SendResult.success("external-msg-001"));

        dispatcher.dispatch(1L);

        final ArgumentCaptor<DeliveryTransitionCommand> captor =
                ArgumentCaptor.forClass(DeliveryTransitionCommand.class);
        then(transitionService).should().transition(eq(delivery), captor.capture());
        assertThat(captor.getValue().event()).isEqualTo(DeliveryEvent.SEND_SUCCESS);
        assertThat(captor.getValue().externalMessageId()).isEqualTo("external-msg-001");
        then(metrics).should().recordSuccess(Channel.EMAIL);
    }

    // ─────────────────────────────────────────────────────────
    // 재시도 시나리오
    // ─────────────────────────────────────────────────────────

    // 재시도 가능 실패 + 재시도 횟수 남음 → FAILED 전이 + nextAttemptAt 설정
    @Test
    @DisplayName("재시도 가능 실패 + canRetry=true → SEND_FAILURE 전이 + nextAttemptAt 설정")
    void dispatch_retryableFailure_canRetry_transitionsToFailed() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, true, 1);
        final Instant expectedNextAttempt = Instant.now().plusSeconds(20);

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery))
                .willReturn(SendResult.retryable(DeliveryErrorCode.NETWORK_ERROR, "connection timeout"));
        given(retryPolicy.nextAttempt(eq(1), any(Instant.class))).willReturn(expectedNextAttempt);

        dispatcher.dispatch(1L);

        final ArgumentCaptor<DeliveryTransitionCommand> captor =
                ArgumentCaptor.forClass(DeliveryTransitionCommand.class);
        then(transitionService).should().transition(eq(delivery), captor.capture());

        final DeliveryTransitionCommand command = captor.getValue();
        assertThat(command.event()).isEqualTo(DeliveryEvent.SEND_FAILURE);
        assertThat(command.nextAttemptAt()).isEqualTo(expectedNextAttempt);
        assertThat(command.reason()).isEqualTo("connection timeout");

        then(metrics).should().recordRetryableFailure(Channel.EMAIL, DeliveryErrorCode.NETWORK_ERROR);
        then(metrics).should(never()).recordExhausted(any());
    }

    // 재시도 가능 실패 + 재시도 횟수 소진 → DEAD 전이 + 소진 메트릭
    @Test
    @DisplayName("재시도 가능 실패 + canRetry=false → EXHAUST 전이 + 소진 메트릭 기록")
    void dispatch_retryableFailure_exhausted_transitionsToDead() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, false, 5);
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery))
                .willReturn(SendResult.retryable(DeliveryErrorCode.NETWORK_ERROR, "connection timeout"));

        dispatcher.dispatch(1L);

        final ArgumentCaptor<DeliveryTransitionCommand> captor =
                ArgumentCaptor.forClass(DeliveryTransitionCommand.class);
        then(transitionService).should().transition(eq(delivery), captor.capture());
        assertThat(captor.getValue().event()).isEqualTo(DeliveryEvent.EXHAUST);

        // 소진 시 RetryPolicy 호출 없음
        then(retryPolicy).should(never()).nextAttempt(any(int.class), any(Instant.class));
        then(metrics).should().recordRetryableFailure(Channel.EMAIL, DeliveryErrorCode.NETWORK_ERROR);
        then(metrics).should().recordExhausted(Channel.EMAIL);
    }

    // 초기 실패에서 재시도 후 성공하는 시나리오 — 재시도 카운트별 nextAttemptAt 증가 확인
    @Test
    @DisplayName("재시도 카운트가 높을수록 RetryPolicy 에 높은 retryCount 전달")
    void dispatch_retryableFailure_passesCorrectRetryCountToPolicy() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.IN_APP, true, 3);
        final Instant nextAttempt = Instant.now().plusSeconds(80);

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery))
                .willReturn(SendResult.retryable(DeliveryErrorCode.EXTERNAL_SERVICE_ERROR, "service down"));
        given(retryPolicy.nextAttempt(eq(3), any(Instant.class))).willReturn(nextAttempt);

        dispatcher.dispatch(1L);

        // retryCount=3 이 그대로 RetryPolicy 에 전달되었는지 검증
        then(retryPolicy).should().nextAttempt(eq(3), any(Instant.class));

        final ArgumentCaptor<DeliveryTransitionCommand> captor =
                ArgumentCaptor.forClass(DeliveryTransitionCommand.class);
        then(transitionService).should().transition(eq(delivery), captor.capture());
        assertThat(captor.getValue().nextAttemptAt()).isEqualTo(nextAttempt);
    }

    // ─────────────────────────────────────────────────────────
    // 비재시도성 실패 시나리오
    // ─────────────────────────────────────────────────────────

    // 비재시도성 실패 → 즉시 DEAD 전이 (canRetry 체크 없음)
    @Test
    @DisplayName("비재시도성 실패 → EXHAUST 즉시 전이 + RetryPolicy 호출 없음")
    void dispatch_nonRetryableFailure_transitionsToDead() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, true, 0);
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery))
                .willReturn(SendResult.nonRetryable(DeliveryErrorCode.INVALID_RECIPIENT, "no such user"));

        dispatcher.dispatch(1L);

        final ArgumentCaptor<DeliveryTransitionCommand> captor =
                ArgumentCaptor.forClass(DeliveryTransitionCommand.class);
        then(transitionService).should().transition(eq(delivery), captor.capture());
        assertThat(captor.getValue().event()).isEqualTo(DeliveryEvent.EXHAUST);

        // 비재시도성 실패는 canRetry 결과와 무관하게 RetryPolicy 호출 없음
        then(retryPolicy).should(never()).nextAttempt(any(int.class), any(Instant.class));
        then(metrics).should().recordNonRetryableFailure(Channel.EMAIL, DeliveryErrorCode.INVALID_RECIPIENT);
        then(metrics).should().recordExhausted(Channel.EMAIL);
    }

    // ─────────────────────────────────────────────────────────
    // 예외 및 동시성 시나리오
    // ─────────────────────────────────────────────────────────

    // delivery 없음 → BusinessException
    @Test
    @DisplayName("존재하지 않는 deliveryId 시 BusinessException 발생")
    void dispatch_deliveryNotFound_throwsBusinessException() {
        given(deliveryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dispatcher.dispatch(999L))
                .isInstanceOf(BusinessException.class);
    }

    // 다른 워커가 이미 처리 → IllegalDeliveryTransitionException 로깅 후 정상 종료
    @Test
    @DisplayName("다른 워커가 이미 처리한 delivery → IllegalDeliveryTransitionException 무시 (재전파 없음)")
    void dispatch_concurrentWorkerAlreadyProcessed_doesNotRethrow() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, true, 0);
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery)).willReturn(SendResult.success("msg-001"));
        given(transitionService.transition(eq(delivery), any()))
                .willThrow(new IllegalDeliveryTransitionException(1L, DeliveryState.SENT, DeliveryEvent.SEND_SUCCESS));

        // 예외가 전파되지 않고 정상 종료되어야 함 (로그만 남김)
        assertThatCode(() -> dispatcher.dispatch(1L)).doesNotThrowAnyException();
    }

    // Sender 예외 → 재전파 (트랜잭션 롤백 → 좀비 복구 대상)
    @Test
    @DisplayName("Sender 예외 시 예외 재전파 — 좀비 복구 워커가 PROCESSING → PENDING 복구")
    void dispatch_senderThrowsException_rethrows() {
        final NotificationDelivery delivery = mockDelivery(1L, Channel.EMAIL, true, 0);
        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery)).willThrow(new RuntimeException("smtp connection refused"));

        assertThatThrownBy(() -> dispatcher.dispatch(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp connection refused");

        // 예외 발생 시 상태전이 없음 → TX 롤백 → delivery 는 PROCESSING 상태 유지 → 좀비 복구 대상
        then(transitionService).should(never()).transition(any(), any());
    }

    // IN_APP 채널에서의 재시도 시나리오
    @Test
    @DisplayName("IN_APP 채널 재시도 가능 실패 → SEND_FAILURE 전이 + 채널 태그로 메트릭 기록")
    void dispatch_inAppRetryableFailure_recordsChannelTaggedMetric() {
        final NotificationDelivery delivery = mockDelivery(2L, Channel.IN_APP, true, 0);
        given(deliveryRepository.findById(2L)).willReturn(Optional.of(delivery));
        given(senderRouter.send(delivery))
                .willReturn(SendResult.retryable(DeliveryErrorCode.EXTERNAL_SERVICE_ERROR, "push server down"));
        given(retryPolicy.nextAttempt(eq(0), any(Instant.class))).willReturn(Instant.now().plusSeconds(10));

        dispatcher.dispatch(2L);

        // 채널 태그가 IN_APP 으로 기록되어야 함
        then(metrics).should().recordRetryableFailure(Channel.IN_APP, DeliveryErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    // 테스트용 — NotificationDelivery mock 생성 (시나리오별 사용 stub 이 달라 lenient 적용)
    private NotificationDelivery mockDelivery(final Long id, final Channel channel,
                                              final boolean canRetry, final int retryCount) {
        final NotificationDelivery delivery = mock(NotificationDelivery.class);
        lenient().when(delivery.getId()).thenReturn(id);
        lenient().when(delivery.getChannel()).thenReturn(channel);
        lenient().when(delivery.canRetry()).thenReturn(canRetry);
        lenient().when(delivery.getRetryCount()).thenReturn(retryCount);
        return delivery;
    }
}
