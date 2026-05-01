package src.alert_system.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import src.alert_system.notification.application.command.DeliveryTransitionCommand;
import src.alert_system.notification.application.service.spec.DeliveryStateTransitionService;
import src.alert_system.notification.domain.entities.NotificationDelivery;
import src.alert_system.notification.domain.enums.DeliveryState;
import src.alert_system.notification.infrastructure.persistence.NotificationDeliveryRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryLockService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryStateTransitionService transitionService;
    

    // 발송 대기 row 락 + PROCESSING 으로 전이 + 발송 대상 ID 리스트 반환 메서드
    @Transactional
    public List<Long> lockAndMarkProcessing(final int batchSize) {
        final Instant now = Instant.now();

        // SELECT FOR UPDATE SKIP LOCKED — 멀티 인스턴스 폴링 안전
        final List<NotificationDelivery> locked =
                deliveryRepository.lockPendingForDispatch(now, batchSize);

        if (locked.isEmpty()) {
            return List.of();
        }

        return locked.stream()
                .map(d -> {
                    transitionService.transition(d, toProcessingCommand(d.getState(), now));
                    return d.getId();
                })
                .toList();
    }

    // 현재 상태에 맞는 PROCESSING 진입 이벤트 선택 메서드 (PENDING→DISPATCH / FAILED→RETRY)
    private DeliveryTransitionCommand toProcessingCommand(final DeliveryState state, final Instant now) {
        return switch (state) {
            case PENDING -> DeliveryTransitionCommand.dispatch(now);
            case FAILED -> DeliveryTransitionCommand.retry(now);
            default -> throw new IllegalStateException(
                    "lockPendingForDispatch 에 예상 외 state 노출 state=" + state);
        };
    }
}
