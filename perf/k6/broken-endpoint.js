import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  sampleBaseUrl,
  envDuration,
  envInt,
  summaryFiles,
} from './common.js';

http.setResponseCallback(http.expectedStatuses(500));

export const options = {
  vus: envInt('BROKEN_VUS', 4),
  duration: envDuration('BROKEN_DURATION', '15s'),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const res = http.get(`${sampleBaseUrl}/broken`);
  check(res, {
    'broken endpoint returned 500': (r) => r.status === 500,
    'broken endpoint kept intentional error payload': (r) => r.json('error') === 'intentional failure',
  });
  sleep(0.1);
}

export function handleSummary(data) {
  return summaryFiles('broken-endpoint', data, {
    vus: options.vus,
    duration: options.duration,
  });
}
