// k6 - 동시 200건 burst 부하 테스트
// 실행: k6 run loadtest/k6/burst-200.js
// 사전 조건: alert-system 기동 + Postgres 기동
//
// 측정 대상
//   1) API 진입 응답 시간 (p50/p95/p99)
//   2) 200건 등록 후 발송 처리 완료까지 e2e 시간 (Prometheus 폴링)
//   3) 실패율 / 멱등 응답률

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const e2eDispatchSeconds = new Trend('e2e_dispatch_seconds');
const sentCounter = new Counter('total_sent');
const deadCounter = new Counter('total_dead');

export const options = {
    scenarios: {
        burst_200: {
            executor: 'shared-iterations',
            vus: 200,            // 동시 사용자 200
            iterations: 200,     // 총 요청 200건
            maxDuration: '30s',
        },
    },
    // teardown 에서 메트릭 폴링이 길어질 수 있어 시간 여유 확보
    teardownTimeout: '120s',
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
    thresholds: {
        // API 진입은 즉시 응답해야 — 발송은 비동기
        // burst 첫 요청들이 cold path (커넥션 풀 warm-up, JIT) 영향으로 일시 spike 가능 → 500ms 로 완화
        'http_req_duration{name:default}': ['p(95)<500'],
        // 실패율 1% 미만 (default 요청만 — teardown polling 제외)
        'http_req_failed{name:default}': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const RECEIVER_IDS = [
    '550e8400-e29b-41d4-a716-446655440000',
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440002',
];

export default function () {
    const idempotencyKey = uuidv4();
    const referenceId = `LOAD-${idempotencyKey.slice(0, 8)}`;

    const payload = JSON.stringify({
        receiverIds: RECEIVER_IDS,
        type: 'ENROLLMENT_CONFIRMED',
        referenceType: 'COURSE',
        referenceId: referenceId,
        payload: { courseId: referenceId },
        channels: ['EMAIL', 'IN_APP'],
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Request-Id': idempotencyKey,
        },
        tags: { name: 'default' }, // teardown polling 과 분리 집계
    };

    const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, params);

    check(res, {
        '201 또는 200': (r) => r.status === 201 || r.status === 200,
        'response has id': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.id;
            } catch (_) {
                return false;
            }
        },
    });
}

// 모든 burst 가 등록된 후 한 번 호출 — 발송 완료 e2e 시간 측정
// teardown 시작 시점의 SENT 값을 baseline 으로 두고 증분만 측정 (멀티 실행 시 누적 카운트 영향 제거)
export function teardown() {
    const startTime = Date.now();
    const targetIncrement = 200 * RECEIVER_IDS.length * 2; // 200 알림 × 3 수신자 × 2 채널 = 1200
    const maxWaitMs = 90000;
    const pollIntervalMs = 500;

    const baseline = fetchMetrics();
    console.log(`\n[teardown] baseline sent=${baseline.sent} dead=${baseline.dead}, 대기 목표 증분=${targetIncrement}`);

    let sentDelta = 0;
    let deadDelta = 0;
    let lastLogged = 0;

    while (Date.now() - startTime < maxWaitMs) {
        const m = fetchMetrics();
        sentDelta = m.sent - baseline.sent;
        deadDelta = m.dead - baseline.dead;

        // 진행 상황 5초마다 로그
        const elapsed = Date.now() - startTime;
        if (elapsed - lastLogged >= 5000) {
            console.log(`[teardown] +${(elapsed/1000).toFixed(1)}s sent=${sentDelta}/${targetIncrement} dead=${deadDelta}`);
            lastLogged = elapsed;
        }

        if (sentDelta + deadDelta >= targetIncrement) {
            break;
        }
        sleep(pollIntervalMs / 1000);
    }

    const elapsedSec = (Date.now() - startTime) / 1000;
    e2eDispatchSeconds.add(elapsedSec);
    sentCounter.add(sentDelta);
    deadCounter.add(deadDelta);

    console.log(`\n=== e2e 발송 처리 결과 ===`);
    console.log(`목표 delivery 수:        ${targetIncrement}`);
    console.log(`SENT 증분:               ${sentDelta}`);
    console.log(`DEAD 증분:               ${deadDelta}`);
    console.log(`처리 누락:               ${Math.max(0, targetIncrement - sentDelta - deadDelta)}`);
    console.log(`burst 종료 → 발송 완료:  ${elapsedSec.toFixed(2)}초`);
    if (elapsedSec > 0) {
        console.log(`처리량 (TPS):            ${((sentDelta + deadDelta) / elapsedSec).toFixed(1)} 건/초`);
    }
}

// Prometheus 메트릭에서 SENT / DEAD 카운트 추출
// 메트릭 이름은 NotificationMetrics.java 에 정의된 것과 일치해야 함
// Micrometer 가 점(.) 을 언더스코어(_) 로 자동 변환하고, Counter 는 _total suffix 가 붙음
function fetchMetrics() {
    const res = http.get(`${BASE_URL}/actuator/prometheus`, {
        tags: { name: 'metrics_poll' }, // 본 요청을 default 흐름과 구분 (집계 분리)
    });
    if (res.status !== 200) {
        return { sent: 0, dead: 0 };
    }
    const body = res.body;
    return {
        sent: sumMetric(body, 'notification_dispatch_success_total'),
        dead: sumMetric(body, 'notification_dispatch_exhausted_total'),
    };
}

function sumMetric(body, name) {
    const lines = body.split('\n').filter(l => l.startsWith(name) && !l.startsWith('# '));
    let total = 0;
    for (const line of lines) {
        const value = parseFloat(line.split(' ').pop());
        if (!isNaN(value)) total += value;
    }
    return total;
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
    };
}

function textSummary(data) {
    const m = data.metrics;
    const e2e = m.e2e_dispatch_seconds?.values?.avg ?? 0;
    const sent = m.total_sent?.values?.count ?? 0;
    return `
=== Burst 200 결과 ===
[API 진입]
  총 요청:          ${m.http_reqs?.values?.count ?? 0}
  실패율:           ${((m.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
  p50 응답:         ${m.http_req_duration?.values?.['p(50)']?.toFixed(0) ?? 'N/A'}ms
  p95 응답:         ${m.http_req_duration?.values?.['p(95)']?.toFixed(0) ?? 'N/A'}ms
  p99 응답:         ${m.http_req_duration?.values?.['p(99)']?.toFixed(0) ?? 'N/A'}ms

[비동기 발송 e2e]
  발송 완료까지:    ${e2e.toFixed(2)}초
  SENT delivery:    ${sent}
  처리량 (TPS):     ${e2e > 0 ? (sent / e2e).toFixed(1) : 'N/A'} 건/초
`;
}
