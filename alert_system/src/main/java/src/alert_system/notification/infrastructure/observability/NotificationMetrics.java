package src.alert_system.notification.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.DeliveryErrorCode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class NotificationMetrics {

    /*
     * Micrometer 메트릭 한 곳 집중 — Sender / Dispatcher / Recovery 가 호출
     * 메트릭 이름은 점(.) 구분 — Prometheus / Datadog / NewRelic 모두 자동 변환됨
     *
     * 노출 경로
     *  - /actuator/metrics              메트릭 목록 + 단건 조회
     *  - /actuator/prometheus           Prometheus scrape 형식
     */

    private static final String DISPATCH_SUCCESS = "notification.dispatch.success";
    private static final String DISPATCH_RETRYABLE = "notification.dispatch.retryable_failure";
    private static final String DISPATCH_NON_RETRYABLE = "notification.dispatch.non_retryable_failure";
    private static final String DISPATCH_EXHAUSTED = "notification.dispatch.exhausted";
    private static final String RECOVERY_TRIGGERED = "notification.recovery.triggered";

    private final MeterRegistry registry;

    /*
     * Counter 캐시 — Tag 조합마다 새로 만들면 비용 누적 → 동일 조합은 재사용
     * key = "metric|tag1=v1,tag2=v2" 형태로 단순 String 결합
     */
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public NotificationMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    // 발송 성공 카운트 메서드
    public void recordSuccess(final Channel channel) {
        counter(DISPATCH_SUCCESS, Tags.of(Tag.of("channel", channel.name()))).increment();
    }

    // 재시도 가능 실패 카운트 메서드
    public void recordRetryableFailure(final Channel channel, final DeliveryErrorCode errorCode) {
        counter(DISPATCH_RETRYABLE, Tags.of(
                Tag.of("channel", channel.name()),
                Tag.of("error_code", errorCode.name())
        )).increment();
    }

    // 비재시도성 실패 카운트 메서드
    public void recordNonRetryableFailure(final Channel channel, final DeliveryErrorCode errorCode) {
        counter(DISPATCH_NON_RETRYABLE, Tags.of(
                Tag.of("channel", channel.name()),
                Tag.of("error_code", errorCode.name())
        )).increment();
    }

    // 최종 실패(DEAD) 카운트 메서드
    public void recordExhausted(final Channel channel) {
        counter(DISPATCH_EXHAUSTED, Tags.of(Tag.of("channel", channel.name()))).increment();
    }

    // 좀비 복구 카운트 메서드
    public void recordRecovery() {
        counter(RECOVERY_TRIGGERED, Tags.empty()).increment();
    }

    // Counter 캐시 조회/생성 메서드
    private Counter counter(final String name, final Tags tags) {
        final String key = name + "|" + tags.stream()
                .map(t -> t.getKey() + "=" + t.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return counterCache.computeIfAbsent(key, k -> registry.counter(name, tags));
    }
}
