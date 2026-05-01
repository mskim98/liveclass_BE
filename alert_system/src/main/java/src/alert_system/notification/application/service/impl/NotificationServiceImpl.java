package src.alert_system.notification.application.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import src.alert_system.common.exception.BusinessException;
import src.alert_system.common.exception.ErrorCode;
import src.alert_system.notification.api.dto.NotificationResponse;
import src.alert_system.notification.application.command.NotificationCreateCommand;
import src.alert_system.notification.application.service.spec.NotificationService;
import src.alert_system.notification.domain.entities.Notification;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationCommandExecutor executor;

    // 알림 등록 메서드
    @Override
    public NotificationResponse createNotification(final NotificationCreateCommand command) {
        // 도메인 정체성으로부터 멱등키 파생
        final String idempotencyKey = Notification.deriveIdempotencyKey(
                command.type(),
                command.referenceType(),
                command.referenceId(),
                command.receiverIds(),
                command.channels());

        // 1단계 — 사전조회로 일반 케이스 빠르게 차단
        final Optional<NotificationResponse> existing = executor.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("멱등키 사전조회 적중 idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        // 신규 INSERT 시도
        try {
            return executor.insertNotification(command, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            // 2단계 — 사전조회와 INSERT 사이 race condition 방어, UNIQUE 위반 시 새 트랜잭션으로 재조회
            log.info("멱등키 race 감지, 기존 row 반환 idempotencyKey={}", idempotencyKey);
            return executor.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, ex));
        }
    }

    // 알림 단건 조회 메서드
    @Override
    public NotificationResponse findById(final String notificationId) {
        return executor.findById(notificationId);
    }
}
