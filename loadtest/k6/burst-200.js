// k6 — 동시 200 burst 부하 테스트
// 실행: k6 run loadtest/k6/burst-200.js
// 사전 조건: alert-system 기동 + Postgres 기동

import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    scenarios: {
        burst_200: {
            executor: 'shared-iterations',
            vus: 200,            // 동시 사용자 200
            iterations: 200,     // 총 요청 200건
            maxDuration: '30s',
        },
    },
    thresholds: {
        // 95% 응답이 300ms 이하 — API 진입은 즉시 응답해야 (발송은 비동기)
        http_req_duration: ['p(95)<300'],
        // 실패율 1% 미만
        http_req_failed: ['rate<0.01'],
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
            'Idempotency-Key': idempotencyKey,
            'X-Request-Id': idempotencyKey,
        },
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

export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
    };
}

function textSummary(data) {
    const m = data.metrics;
    return `
=== Burst 200 결과 ===
총 요청:          ${m.http_reqs?.values?.count ?? 0}
실패율:           ${((m.http_req_failed?.values?.rate ?? 0) * 100).toFixed(2)}%
p50 응답시간:     ${m.http_req_duration?.values?.['p(50)']?.toFixed(0) ?? 'N/A'}ms
p95 응답시간:     ${m.http_req_duration?.values?.['p(95)']?.toFixed(0) ?? 'N/A'}ms
p99 응답시간:     ${m.http_req_duration?.values?.['p(99)']?.toFixed(0) ?? 'N/A'}ms
총 소요 시간:     ${(m.iteration_duration?.values?.max / 1000)?.toFixed(2) ?? 'N/A'}s
`;
}