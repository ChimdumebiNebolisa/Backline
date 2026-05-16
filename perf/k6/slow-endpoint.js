import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  sampleBaseUrl,
  envDuration,
  envInt,
  summaryFiles,
} from './common.js';

export const options = {
  vus: envInt('SLOW_VUS', 4),
  duration: envDuration('SLOW_DURATION', '15s'),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<3000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const res = http.get(`${sampleBaseUrl}/slow`);
  check(res, {
    'slow endpoint returned 200': (r) => r.status === 200,
    'slow endpoint payload has delayedMs': (r) => r.json('delayedMs') === 700,
    'slow endpoint payload has ok status': (r) => r.json('status') === 'ok',
  });
  sleep(0.1);
}

export function handleSummary(data) {
  return summaryFiles('slow-endpoint', data, {
    vus: options.vus,
    duration: options.duration,
  });
}
