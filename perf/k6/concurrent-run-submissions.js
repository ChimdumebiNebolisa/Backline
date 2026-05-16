import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import {
  apiBaseUrl,
  envDuration,
  envInt,
  upsertProjectAndChecks,
  createRunBody,
  summaryFiles,
  apiExpectedStatuses,
} from './common.js';

const VUS = envInt('SUBMISSION_VUS', 6);
const ITERATIONS = envInt('SUBMISSION_ITERATIONS', 24);
const MAX_DURATION = envDuration('SUBMISSION_MAX_DURATION', '45s');

export const options = {
  scenarios: {
    submit_runs: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:submit_run}': ['p(95)<5000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return upsertProjectAndChecks('submission');
}

export default function (project) {
  const suffix = `${exec.scenario.iterationInTest}-${__VU}-${Date.now()}`;
  const res = http.post(`${apiBaseUrl}/api/runs`, JSON.stringify(createRunBody(project, suffix)), {
    headers: { 'Content-Type': 'application/json' },
    responseCallback: apiExpectedStatuses,
    tags: { name: 'submit_run' },
  });

  check(res, {
    'run submission returned 201': (r) => r.status === 201,
    'run id returned': (r) => Boolean(r.json('data.id')),
    'run enters known lifecycle state': (r) => {
      const status = r.json('data.status');
      return ['QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR'].includes(status);
    },
  });
}

export function handleSummary(data) {
  return summaryFiles('concurrent-run-submissions', data, {
    vus: VUS,
    iterations: ITERATIONS,
    maxDuration: MAX_DURATION,
  });
}
