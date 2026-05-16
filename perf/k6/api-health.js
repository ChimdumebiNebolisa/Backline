import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  apiBaseUrl,
  envDuration,
  envInt,
  summaryFiles,
  apiExpectedStatuses,
} from './common.js';

export const options = {
  vus: envInt('HEALTH_VUS', 4),
  duration: envDuration('HEALTH_DURATION', '20s'),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const actuator = http.get(`${apiBaseUrl}/actuator/health`, { responseCallback: apiExpectedStatuses });
  check(actuator, {
    'actuator health returned 200': (r) => r.status === 200,
    'actuator health is UP': (r) => r.json('status') === 'UP',
  });

  const api = http.get(`${apiBaseUrl}/api/health`, { responseCallback: apiExpectedStatuses });
  check(api, {
    'api health returned 200': (r) => r.status === 200,
    'api health reports UP': (r) => r.json('data.status') === 'UP',
  });

  sleep(0.2);
}

export function handleSummary(data) {
  return summaryFiles('api-health', data, {
    vus: options.vus,
    duration: options.duration,
  });
}
