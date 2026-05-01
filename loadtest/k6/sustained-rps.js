// k6 — 지속 부하 (50 RPS × 60초) — 풀 사이즈 / 백프레셔 정상 동작 검증
// 실행: k6 run loadtest/k6/sustained-rps.js

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    scenarios: {
        sustained: {
            executor: 'constant-arrival-rate',
            rate: 50,                  // 초당 50 요청
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<300'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        receiverIds: ['550e8400-e29b-41d4-a716-446655440000'],
        type: 'ENROLLMENT_CONFIRMED',
        referenceType: 'COURSE',
        referenceId: `RPS-${idempotencyKey.slice(0, 8)}`,
        payload: {},
        channels: ['EMAIL'],
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
    };

    const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, params);

    check(res, {
        '성공 응답': (r) => r.status === 201 || r.status === 200,
    });
}