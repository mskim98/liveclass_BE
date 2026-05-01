package src.alert_system.notification.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.common.exception.GlobalExceptionHandler;
import src.alert_system.notification.api.dto.NotificationCreateRequest;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.api.mapper.NotificationRequestMapper;
import src.alert_system.notification.application.service.spec.NotificationService;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({NotificationRequestMapper.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private NotificationService notificationService;

    // 신규 등록 — 정상 케이스 (201 Created + ApiResponse.success body)
    @Test
    @DisplayName("POST /api/v1/notifications — 신규 등록 시 201 + 본문 반환")
    void createNotification_returns201() throws Exception {
        final NotificationCreateRequest request = sampleRequest();
        final NotificationResponse response = sampleResponse("01HFAKE000000000000000001");

        given(notificationService.createNotification(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("01HFAKE000000000000000001"))
                .andExpect(jsonPath("$.data.deliveries").isArray());
    }

    // 잘못된 JSON — 400 + INVALID_REQUEST
    @Test
    @DisplayName("POST /api/v1/notifications — 잘못된 JSON 시 400 + INVALID_REQUEST")
    void createNotification_invalidJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.getCode()));
    }

    // 단건 조회 — 정상 케이스 (200 OK)
    @Test
    @DisplayName("GET /api/v1/notifications/{id} — 존재 시 200")
    void findById_returns200() throws Exception {
        final NotificationResponse response = sampleResponse("01HFAKE000000000000000002");
        given(notificationService.findById("01HFAKE000000000000000002")).willReturn(response);

        mockMvc.perform(get("/api/v1/notifications/{id}", "01HFAKE000000000000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("01HFAKE000000000000000002"));
    }

    // 단건 조회 — 없는 알림 (404 + NOTIFICATION_NOT_FOUND)
    @Test
    @DisplayName("GET /api/v1/notifications/{id} — 미존재 시 404 + NOTIFICATION_NOT_FOUND")
    void findById_notFound_returns404() throws Exception {
        given(notificationService.findById("01HFAKE000000000000000404"))
                .willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/notifications/{id}", "01HFAKE000000000000000404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.NOTIFICATION_NOT_FOUND.getCode()));
    }

    // 테스트용 메서드
    private NotificationCreateRequest sampleRequest() {
        return new NotificationCreateRequest(
                List.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                Map.of("courseId", "C001"),
                List.of(Channel.EMAIL, Channel.IN_APP),
                null);
    }

    // 테스트용 메서드
    private NotificationResponse sampleResponse(final String id) {
        return new NotificationResponse(
                id,
                "derived-key-" + id,
                NotificationType.ENROLLMENT_CONFIRMED,
                "COURSE",
                "C001",
                Map.of("courseId", "C001"),
                null,
                Instant.now(),
                List.of());
    }
}
