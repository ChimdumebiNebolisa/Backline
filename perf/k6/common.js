import http from 'k6/http';

const PROJECTS = JSON.parse(open('../data/projects.json'));

export const apiBaseUrl = (__ENV.BACKLINE_API_URL || 'http://localhost:8080').replace(/\/$/, '');
export const sampleBaseUrl = (__ENV.SAMPLE_API_URL || 'http://localhost:8081').replace(/\/$/, '');
export const perfOutDir = (__ENV.PERF_OUT_DIR || '/perf/out').replace(/\/$/, '');
export const perfLabel = __ENV.PERF_LABEL || 'adhoc';
export const apiExpectedStatuses = http.expectedStatuses(200, 201);

export function envInt(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === null || raw === '') {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function envDuration(name, fallback) {
  return __ENV[name] || fallback;
}

export function loadProject(name) {
  const project = PROJECTS[name];
  if (!project) {
    throw new Error(`Unknown project definition: ${name}`);
  }
  return JSON.parse(JSON.stringify(project));
}

function jsonHeaders(extra) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra || {});
}

export function upsertProjectAndChecks(projectName) {
  const project = loadProject(projectName);
  const projectExpectedStatuses = http.expectedStatuses(201, 409);
  const createProjectRes = http.post(
    `${apiBaseUrl}/api/projects`,
    JSON.stringify({ slug: project.slug, name: project.name }),
    { headers: jsonHeaders(), responseCallback: projectExpectedStatuses, tags: { name: 'create_project' } }
  );
  if (createProjectRes.status !== 201 && createProjectRes.status !== 409) {
    throw new Error(`Project upsert failed for ${project.slug}: HTTP ${createProjectRes.status}`);
  }

  const syncBody = {
    projectSlug: project.slug,
    projectName: project.name,
    checks: project.checks,
  };
  const syncRes = http.post(`${apiBaseUrl}/api/checks/sync`, JSON.stringify(syncBody), {
    headers: jsonHeaders(),
    responseCallback: apiExpectedStatuses,
    tags: { name: 'sync_checks' },
  });
  if (syncRes.status < 200 || syncRes.status >= 300) {
    throw new Error(`Check sync failed for ${project.slug}: HTTP ${syncRes.status}`);
  }
  return project;
}

export function createRunBody(project, suffix) {
  return {
    projectSlug: project.slug,
    environment: project.environment,
    configHash: project.configHash,
    idempotencyKey: `${project.slug}-${perfLabel}-${suffix}`,
    source: `perf-${perfLabel}`,
  };
}

export function summaryFiles(testName, data, extra) {
  const summary = {
    testName,
    label: perfLabel,
    timestamp: new Date().toISOString(),
    options: data.options,
    rootGroup: data.root_group,
    state: data.state,
    metrics: data.metrics,
    extra: extra || {},
  };

  const durationMetric = data.metrics.http_req_duration || { values: {} };
  const failedMetric = data.metrics.http_req_failed || { values: {} };
  const checksMetric = data.metrics.checks || { values: {} };
  const lines = [
    `test=${testName}`,
    `label=${perfLabel}`,
    `requests=${(data.metrics.http_reqs && data.metrics.http_reqs.values && data.metrics.http_reqs.values.count) || 0}`,
    `http_req_failed_rate=${failedMetric.values.rate || 0}`,
    `checks_rate=${checksMetric.values.rate || 0}`,
    `http_req_duration_p95=${durationMetric.values['p(95)'] || 0}`,
    `http_req_duration_p99=${durationMetric.values['p(99)'] || 0}`,
  ];
  if (extra) {
    Object.keys(extra)
      .sort()
      .forEach((key) => lines.push(`${key}=${JSON.stringify(extra[key])}`));
  }

  return {
    [`${perfOutDir}/${testName}-${perfLabel}-summary.json`]: JSON.stringify(summary, null, 2),
    [`${perfOutDir}/${testName}-${perfLabel}-summary.txt`]: `${lines.join('\n')}\n`,
  };
}
