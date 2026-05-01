package src.alert_system.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMdcFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    /*
     * 모든 HTTP 요청에 requestId 부여 → MDC 에 적재
     * → 로그 패턴에 %X{requestId} 로 출력 → 한 요청의 전체 로그 시퀀스 추적 가능
     * 클라이언트가 X-Request-Id 헤더 보내면 그 값 사용 (분산 시스템 trace 연결), 없으면 UUID 생성
     */

    // 요청 진입 시 MDC 적재 / 응답 후 정리 메서드
    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    // 요청 헤더 또는 새 UUID 로 requestId 결정 메서드
    private String resolveRequestId(final HttpServletRequest request) {
        final String fromHeader = request.getHeader(HEADER_REQUEST_ID);
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        return UUID.randomUUID().toString();
    }
}
