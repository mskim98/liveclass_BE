package src.alert_system.notification.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.common.exception.GlobalExceptionHandler;
import src.alert_system.notification.api.dto.UserNotificationResponse;
import src.alert_system.notification.application.service.spec.NotificationService;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserNotificationController.class)
@Import(GlobalExceptionHandler.class)
class UserNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    // 필터 없이 목록 조회 — 200 + 배열 반환
    @Test
    @DisplayName("GET /api/v1/users/{userId}/notifications — 필터 없이 목록 200")
    void listMyNotifications_noFilter_returns200() throws Exception {
        given(notificationService.findUserNotifications(eq(userId), isNull(), isNull()))
                .willReturn(List.of(
                        sampleResponse(1L, Channel.EMAIL),
                        sampleResponse(2L, Channel.IN_APP)
                ));

        mockMvc.perform(get("/api/v1/users/{userId}/notifications", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ?channel=EMAIL 필터 — 해당 채널 결과만 반환
    @Test
    @DisplayName("GET /api/v1/users/{userId}/notifications?channel=EMAIL — 채널 필터 200")
    void listMyNotifications_channelFilter_returns200() throws Exception {
        given(notificationService.findUserNotifications(eq(userId), eq(Channel.EMAIL), isNull()))
                .willReturn(List.of(sampleResponse(1L, Channel.EMAIL)));

        mockMvc.perform(get("/api/v1/users/{userId}/notifications", userId)
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].channel").value("EMAIL"));
    }

    // ?read=false 필터 — 읽지 않은 알림만 반환
    @Test
    @DisplayName("GET /api/v1/users/{userId}/notifications?read=false — 미읽음 필터 200")
    void listMyNotifications_readFilter_returns200() throws Exception {
        given(notificationService.findUserNotifications(eq(userId), isNull(), eq(false)))
                .willReturn(List.of(sampleResponse(1L, Channel.IN_APP)));

        mockMvc.perform(get("/api/v1/users/{userId}/notifications", userId)
                        .param("read", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // 조회 결과 없을 때 — 200 + 빈 배열
    @Test
    @DisplayName("GET /api/v1/users/{userId}/notifications — 결과 없을 때 200 + 빈 배열")
    void listMyNotifications_empty_returns200WithEmptyArray() throws Exception {
        given(notificationService.findUserNotifications(eq(userId), isNull(), isNull()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/users/{userId}/notifications", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // 읽음 처리 성공 — 204 No Content
    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/deliveries/{deliveryId}/read — 읽음 처리 204")
    void markRead_returns204() throws Exception {
        willDoNothing().given(notificationService).markRead(userId, 42L);

        mockMvc.perform(patch("/api/v1/users/{userId}/deliveries/{deliveryId}/read", userId, 42L))
                .andExpect(status().isNoContent());
    }

    // 없는 deliveryId — 404 + NOTIFICATION_NOT_FOUND
    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/deliveries/{deliveryId}/read — 미존재 delivery 404")
    void markRead_notFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).markRead(userId, 999L);

        mockMvc.perform(patch("/api/v1/users/{userId}/deliveries/{deliveryId}/read", userId, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.NOTIFICATION_NOT_FOUND.getCode()));
    }

    private UserNotificationResponse sampleResponse(final Long deliveryId, final Channel channel) {
        return new UserNotificationResponse(
                deliveryId,
                "01HFAKE000000000000000001",
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                channel,
                DeliveryState.SENT,
                Map.of("courseId", "C001"),
                Instant.now(),
                null,
                Instant.now()
        );
    }
}
