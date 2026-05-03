package src.alert_system.notification.application.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.api.dto.UserNotificationResponse;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.application.mapper.NotificationResponseMapper;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.domain.entities.Notification;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.domain.enums.NotificationType;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private NotificationCommandExecutor executor;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private DeliveryStateTransitionService transitionService;

    @Mock
    private NotificationResponseMapper responseMapper;

    // 멱등키 사전조회 적중 — insertNotification 호출 없이 기존 응답 반환
    @Test
    @DisplayName("createNotification — 멱등키 사전조회 적중 시 insert 건너뛰고 기존 응답 반환")
    void createNotification_idempotentKey_preCheckHit() {
        final NotificationCreateCommand command = sampleCommand();
        final NotificationResponse existing = sampleResponse();

        given(executor.findByIdempotencyKey(any())).willReturn(Optional.of(existing));

        final NotificationResponse result = notificationService.createNotification(command);

        assertThat(result).isEqualTo(existing);
        then(executor).should(never()).insertNotification(any(), any());
    }

    // 사전조회 miss → insert 수행
    @Test
    @DisplayName("createNotification — 사전조회 miss 시 insertNotification 호출 후 응답 반환")
    void createNotification_newInsert_succeeds() {
        final NotificationCreateCommand command = sampleCommand();
        final NotificationResponse newResponse = sampleResponse();

        given(executor.findByIdempotencyKey(any())).willReturn(Optional.empty());
        given(executor.insertNotification(eq(command), any())).willReturn(newResponse);

        final NotificationResponse result = notificationService.createNotification(command);

        assertThat(result).isEqualTo(newResponse);
        then(executor).should().insertNotification(eq(command), any());
    }

    // DataIntegrityViolationException 발생 시 재조회 → 기존 응답 반환 (race condition 방어)
    @Test
    @DisplayName("createNotification — UNIQUE 위반 시 재조회 응답 반환 (race condition 방어)")
    void createNotification_raceCondition_fallsBackToFind() {
        final NotificationCreateCommand command = sampleCommand();
        final NotificationResponse recovered = sampleResponse();

        given(executor.findByIdempotencyKey(any()))
                .willReturn(Optional.empty())             // 1차 사전조회 miss
                .willReturn(Optional.of(recovered));      // 2차 복구 조회 적중
        given(executor.insertNotification(any(), any()))
                .willThrow(new DataIntegrityViolationException("uk_violation"));

        final NotificationResponse result = notificationService.createNotification(command);

        assertThat(result).isEqualTo(recovered);
    }

    // receiverId 일치 → readAt 설정
    @Test
    @DisplayName("markRead — receiverId 일치 시 readAt 설정")
    void markRead_success() {
        final UUID userId = UUID.randomUUID();
        final NotificationDelivery delivery = NotificationDelivery.builder()
                .receiverId(userId)
                .channel(Channel.IN_APP)
                .maxRetries(3)
                .nextAttemptAt(Instant.now())
                .build();

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));

        notificationService.markRead(userId, 1L);

        assertThat(delivery.getReadAt()).isNotNull();
    }

    // receiverId 불일치 → NOTIFICATION_NOT_FOUND
    @Test
    @DisplayName("markRead — receiverId 불일치 시 NOTIFICATION_NOT_FOUND 예외")
    void markRead_wrongOwner_throws() {
        final UUID ownerId = UUID.randomUUID();
        final NotificationDelivery delivery = NotificationDelivery.builder()
                .receiverId(ownerId)
                .channel(Channel.IN_APP)
                .maxRetries(3)
                .nextAttemptAt(Instant.now())
                .build();

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));

        assertThatThrownBy(() -> notificationService.markRead(UUID.randomUUID(), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    // 이미 읽음 상태 → readAt 변경 없이 유지 (멱등)
    @Test
    @DisplayName("markRead — 이미 읽음 상태면 readAt 최초 값 유지 (멱등)")
    void markRead_alreadyRead_idempotent() {
        final UUID userId = UUID.randomUUID();
        final NotificationDelivery delivery = NotificationDelivery.builder()
                .receiverId(userId)
                .channel(Channel.IN_APP)
                .maxRetries(3)
                .nextAttemptAt(Instant.now())
                .build();

        final Instant firstReadAt = Instant.parse("2024-01-01T00:00:00Z");
        delivery.markRead(firstReadAt);

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));

        notificationService.markRead(userId, 1L); // 2차 호출

        assertThat(delivery.getReadAt()).isEqualTo(firstReadAt);
    }

    // 없는 deliveryId → NOTIFICATION_NOT_FOUND
    @Test
    @DisplayName("markRead — 존재하지 않는 deliveryId 시 NOTIFICATION_NOT_FOUND")
    void markRead_deliveryNotFound_throws() {
        given(deliveryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(UUID.randomUUID(), 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    // retryDeadDelivery — 없는 deliveryId
    @Test
    @DisplayName("retryDeadDelivery — 존재하지 않는 deliveryId 시 NOTIFICATION_NOT_FOUND")
    void retryDeadDelivery_notFound_throws() {
        given(deliveryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.retryDeadDelivery("NOTIF001", 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    // retryDeadDelivery — URL notificationId 와 delivery 부모 불일치
    @Test
    @DisplayName("retryDeadDelivery — notificationId 불일치 시 NOTIFICATION_NOT_FOUND")
    void retryDeadDelivery_notificationIdMismatch_throws() {
        final NotificationDelivery delivery = mock(NotificationDelivery.class);
        final Notification notification = mock(Notification.class);

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(delivery.getNotification()).willReturn(notification);
        given(notification.getId()).willReturn("DIFFERENT_ID");

        assertThatThrownBy(() -> notificationService.retryDeadDelivery("NOTIF001", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    // retryDeadDelivery — 정상 케이스: resetForManualRetry 후 MANUAL_RETRY 이벤트 전이
    @Test
    @DisplayName("retryDeadDelivery — 정상 케이스: resetForManualRetry 호출 후 상태전이 수행")
    void retryDeadDelivery_success() {
        final NotificationDelivery delivery = mock(NotificationDelivery.class);
        final Notification notification = mock(Notification.class);

        given(deliveryRepository.findById(1L)).willReturn(Optional.of(delivery));
        given(delivery.getNotification()).willReturn(notification);
        given(notification.getId()).willReturn("NOTIF001");
        given(transitionService.transition(eq(delivery), any(DeliveryTransitionCommand.class)))
                .willReturn(DeliveryState.PENDING);

        notificationService.retryDeadDelivery("NOTIF001", 1L);

        then(delivery).should().resetForManualRetry();
        then(transitionService).should().transition(eq(delivery), any(DeliveryTransitionCommand.class));
    }

    // findUserNotifications — 필터 파라미터 repository 에 그대로 전달
    @Test
    @DisplayName("findUserNotifications — 필터 파라미터 그대로 repository 에 위임")
    void findUserNotifications_delegatesToRepository() {
        final UUID userId = UUID.randomUUID();
        final Channel channel = Channel.EMAIL;
        final Boolean readFilter = false;
        final NotificationDelivery delivery = mock(NotificationDelivery.class);
        final UserNotificationResponse expected = sampleUserResponse();

        given(deliveryRepository.findUserDeliveries(userId, channel, readFilter))
                .willReturn(List.of(delivery));
        given(responseMapper.toUserResponse(delivery)).willReturn(expected);

        final List<UserNotificationResponse> result =
                notificationService.findUserNotifications(userId, channel, readFilter);

        assertThat(result).containsExactly(expected);
    }

    // 필터 null 시 repository 에 null 파라미터 전달 (전체 조회)
    @Test
    @DisplayName("findUserNotifications — 필터 null 시 null 그대로 repository 전달")
    void findUserNotifications_noFilter_passesNullToRepository() {
        final UUID userId = UUID.randomUUID();

        given(deliveryRepository.findUserDeliveries(eq(userId), isNull(), isNull()))
                .willReturn(List.of());

        final List<UserNotificationResponse> result =
                notificationService.findUserNotifications(userId, null, null);

        assertThat(result).isEmpty();
    }

    private NotificationCreateCommand sampleCommand() {
        return new NotificationCreateCommand(
                List.of(UUID.randomUUID()),
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                Map.of("courseId", "C001"),
                List.of(Channel.EMAIL),
                null
        );
    }

    private NotificationResponse sampleResponse() {
        return new NotificationResponse(
                "01HFAKE000000000000000001",
                "derived-key",
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                Map.of("courseId", "C001"),
                null,
                Instant.now(),
                List.of()
        );
    }

    private UserNotificationResponse sampleUserResponse() {
        return new UserNotificationResponse(
                1L,
                "01HFAKE000000000000000001",
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                Channel.EMAIL,
                DeliveryState.SENT,
                Map.of("courseId", "C001"),
                Instant.now(),
                null,
                Instant.now()
        );
    }
}
