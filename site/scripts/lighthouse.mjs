import { spawn } from 'node:child_process';
import { mkdir, readFile, rm } from 'node:fs/promises';
import path from 'node:path';

const siteURL = 'http://127.0.0.1:4173/';
const outputDirectory = path.resolve('.lighthouseci');
const thresholds = {
  performance: 0.95,
  accessibility: 1,
  'best-practices': 0.95,
  seo: 0.95,
};

await rm(outputDirectory, { recursive: true, force: true });
await mkdir(outputDirectory, { recursive: true });

const preview = spawn(
  process.execPath,
  [path.resolve('node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1'],
  { stdio: ['ignore', 'ignore', 'pipe'] },
);
let previewError = '';
preview.stderr.setEncoding('utf8');
preview.stderr.on('data', (chunk) => {
  previewError += chunk;
});

try {
  await waitForServer(preview);
  for (let run = 1; run <= 3; run += 1) {
    const reportPath = path.join(outputDirectory, `lhr-${run}.json`);
    const chromeFlags = process.env.LIGHTHOUSE_CHROME_FLAGS?.trim() || '--headless=new';
    await execute(process.execPath, [
      path.resolve('node_modules/lighthouse/cli/index.js'),
      siteURL,
      '--quiet',
      '--output=json',
      `--output-path=${reportPath}`,
      `--chrome-flags=${chromeFlags}`,
    ]);

    const report = JSON.parse(await readFile(reportPath, 'utf8'));
    const scores = [];
    for (const [category, minimum] of Object.entries(thresholds)) {
      const score = report.categories?.[category]?.score;
      if (typeof score !== 'number') {
        throw new Error(`Lighthouse run ${run} did not produce a ${category} score`);
      }
      scores.push(`${category}=${Math.round(score * 100)}`);
      if (score < minimum) {
        throw new Error(
          `Lighthouse run ${run} ${category} score ${score.toFixed(2)} is below ${minimum.toFixed(2)}`,
        );
      }
    }
    console.log(`Lighthouse run ${run}/3 passed: ${scores.join(' ')}`);
  }
} finally {
  await stop(preview);
}

async function waitForServer(processHandle) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null || processHandle.signalCode !== null) {
      throw new Error(`Vite preview exited before readiness: ${previewError.trim()}`);
    }
    try {
      const response = await fetch(siteURL, { signal: AbortSignal.timeout(1_000) });
      if (response.ok) return;
    } catch {
      // Readiness is retried until the bounded deadline.
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Vite preview did not become ready within 30 seconds: ${previewError.trim()}`);
}

function execute(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit' });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`Lighthouse exited with code ${code ?? 'none'} signal ${signal ?? 'none'}`));
    });
  });
}

async function stop(processHandle) {
  if (processHandle.exitCode !== null || processHandle.signalCode !== null) return;
  processHandle.kill('SIGTERM');
  await Promise.race([
    new Promise((resolve) => processHandle.once('exit', resolve)),
    new Promise((resolve) => setTimeout(resolve, 5_000)),
  ]);
  if (processHandle.exitCode === null && processHandle.signalCode === null) {
    processHandle.kill('SIGKILL');
  }
}
