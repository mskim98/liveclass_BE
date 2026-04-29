package src.alert_system.notification.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import src.alert_system.common.response.ApiResponse;
import src.alert_system.notification.api.dto.NotificationCreateRequest;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.api.mapper.NotificationRequestMapper;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.application.service.spec.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRequestMapper notificationRequestMapper;

    // 알림 등록 엔드포인트 메서드
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> create(
            @Valid @RequestBody final NotificationCreateRequest request) {

        final NotificationCreateCommand command = notificationRequestMapper.toCommand(request);
        final NotificationResponse response = notificationService.createNotification(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successResponse(response));
    }

    // 알림 단건 조회 엔드포인트 메서드
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationResponse>> findById(
            @PathVariable final String notificationId) {

        final NotificationResponse response = notificationService.findById(notificationId);

        return ResponseEntity.ok(ApiResponse.successResponse(response));
    }
}
