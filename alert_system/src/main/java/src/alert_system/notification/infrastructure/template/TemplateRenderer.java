package src.alert_system.notification.infrastructure.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import src.alert_system.notification.domain.enums.Channel;
import src.alert_system.notification.domain.enums.NotificationType;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final NotificationTemplateStore store;

    // 타입·채널·페이로드를 이용해 제목과 본문을 렌더링하는 메서드
    public RenderedMessage render(final NotificationType type,
                                  final Channel channel,
                                  final Map<String, Object> payload) {
        final NotificationTemplateEntry template = store.find(type, channel)
                .orElseGet(() -> {
                    log.warn("[template] 미등록 템플릿 type={} channel={} — 기본값 사용", type, channel);
                    return new NotificationTemplateEntry(type.name(), String.valueOf(payload));
                });

        return new RenderedMessage(
                resolve(template.titleTemplate(), payload),
                resolve(template.bodyTemplate(), payload));
    }

    // {key} 플레이스홀더를 payload 값으로 치환하는 메서드
    private String resolve(final String template, final Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return template;
        }
        final Matcher matcher = PLACEHOLDER.matcher(template);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String key = matcher.group(1);
            final Object value = payload.getOrDefault(key, "{" + key + "}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
