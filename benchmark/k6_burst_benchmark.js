import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    burst_scheduling: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '10s', target: 500 },  // Ramp up to 500 schedules/sec
        { duration: '40s', target: 1000 }, // Sustained burst
        { duration: '10s', target: 0 },    // Ramp down
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],    // Under 1% failures
    http_req_duration: ['p(95)<150'],  // 95% of schedule calls under 150ms
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Random delay between 5 and 60 seconds
  const delaySec = Math.floor(Math.random() * 55) + 5;
  const scheduledTime = new Date(Date.now() + delaySec * 1000).toISOString();
  const taskKey = `k6-task-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    idempotencyKey: taskKey,
    type: 'WEBHOOK',
    target: 'https://httpbin.org/post',
    payload: JSON.stringify({ iter: __ITER, vu: __VU }),
    scheduledTime: scheduledTime,
    retryPolicy: {
      maxAttempts: 3,
      initialIntervalMs: 1000,
      multiplier: 2.0,
      jitterFactor: 0.1,
    },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': taskKey,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/tasks`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
    'has task id': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body && body.id !== undefined;
      } catch (e) {
        return false;
      }
    },
  });
}
