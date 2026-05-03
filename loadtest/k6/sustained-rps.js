// k6 - 지속 부하 (200 RPS × 60초) - 풀 사이즈 / 백프레셔 정상 동작 검증
// 실행: k6 run loadtest/k6/sustained-rps.js
//
// 측정 대상
//   1) 200 RPS 지속 시 API 진입 응답 시간 안정성
//   2) ThreadPool / Hikari 풀 사이즈 한계 노출 여부 (실패율 / 큐 포화 백프레셔)
//   3) Worker 처리량이 입력 RPS 를 따라가는지 (Prometheus 폴링)

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const TARGET_RPS = parseInt(__ENV.TARGET_RPS || '200');
const DURATION = __ENV.DURATION || '60s';

export const options = {
    scenarios: {
        sustained: {
            executor: 'constant-arrival-rate',
            rate: TARGET_RPS,                  // 초당 N 요청 (기본 200)
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: TARGET_RPS,       // RPS 와 동일하게 시작
            maxVUs: TARGET_RPS * 2,            // 응답 지연 시 VU 자동 확장 여지
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const RECEIVERS = [
    '550e8400-e29b-41d4-a716-446655440000',
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440003',
];

export default function () {
    const idempotencyKey = uuidv4();
    const receiver = RECEIVERS[Math.floor(Math.random() * RECEIVERS.length)];

    const payload = JSON.stringify({
        receiverIds: [receiver],
        type: 'ENROLLMENT_CONFIRMED',
        referenceType: 'COURSE',
        referenceId: `RPS-${idempotencyKey.slice(0, 8)}`,
        payload: { courseId: idempotencyKey.slice(0, 8) },
        channels: ['EMAIL'],
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, params);

    check(res, {
        '성공 응답': (r) => r.status === 201 || r.status === 200,
    });
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
    };
}

function textSummary(data) {
    const m = data.metrics;
    return `
=== Sustained ${TARGET_RPS} RPS × ${DURATION} 결과 ===
총 요청:          ${m.http_reqs?.values?.count ?? 0}
실제 처리량 RPS:  ${m.http_reqs?.values?.rate?.toFixed(1) ?? 'N/A'}
실패율:           ${((m.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
p50 응답시간:     ${m.http_req_duration?.values?.['p(50)']?.toFixed(0) ?? 'N/A'}ms
p95 응답시간:     ${m.http_req_duration?.values?.['p(95)']?.toFixed(0) ?? 'N/A'}ms
p99 응답시간:     ${m.http_req_duration?.values?.['p(99)']?.toFixed(0) ?? 'N/A'}ms
max 응답시간:     ${m.http_req_duration?.values?.max?.toFixed(0) ?? 'N/A'}ms

힌트
  - p95 가 폭증 → ThreadPool / Hikari 한계 도달
  - 실패율 ≥ 1% → DB 커넥션 timeout 가능, 풀 확장 검토
  - Worker 처리량은 /actuator/prometheus 의 notification_send_success_total 로 별도 확인
`;
}
