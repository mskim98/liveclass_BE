package src.alert_system.notification.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import src.alert_system.common.response.ApiResponse;
import src.alert_system.notification.api.dto.UserNotificationResponse;
import src.alert_system.notification.application.service.spec.NotificationService;
import src.alert_system.notification.domain.enums.Channel;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class UserNotificationController {

    private final NotificationService notificationService;

    // 사용자 본인 알림 목록 조회 엔드포인트 메서드 (channel / read 필터 옵션)
    @GetMapping("/notifications")
    public ApiResponse<List<UserNotificationResponse>> listMyNotifications(
            @PathVariable final UUID userId,
            @RequestParam(required = false) final Channel channel,
            @RequestParam(required = false) final Boolean read) {

        final List<UserNotificationResponse> notifications =
                notificationService.findUserNotifications(userId, channel, read);

        return ApiResponse.successResponse(notifications);
    }

    // 인앱 알림 읽음 처리 엔드포인트 메서드
    @PatchMapping("/deliveries/{deliveryId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @PathVariable final UUID userId,
            @PathVariable final Long deliveryId) {

        notificationService.markRead(userId, deliveryId);
    }
}
