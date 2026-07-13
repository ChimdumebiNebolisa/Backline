import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import test from 'node:test';

const [html, css, terminalCapture, reportCapture] = await Promise.all([
  readFile(new URL('../index.html', import.meta.url), 'utf8'),
  readFile(new URL('../src/style.css', import.meta.url), 'utf8'),
  stat(new URL('../public/demo/failed-run-diff.webp', import.meta.url)),
  stat(new URL('../public/demo/markdown-report.webp', import.meta.url)),
]);

test('landing page exposes the verified product narrative', () => {
  for (const phrase of [
    'backline run',
    'backline history',
    'backline diff',
    'backline report',
    'exit 5',
    'PostgreSQL',
    'Hosted monitoring',
    'What Backline does not do.',
  ]) {
    assert.ok(html.includes(phrase), `expected page to contain ${phrase}`);
  }
});

test('landing page includes accessible structure and repository links', () => {
  for (const phrase of [
    'Skip to content',
    'aria-label="Primary navigation"',
    'aria-label="backline - home"',
    'aria-controls="site-nav"',
    'https://github.com/ChimdumebiNebolisa/Backline',
  ]) {
    assert.ok(html.includes(phrase), `expected page to contain ${phrase}`);
  }
  assert.ok(css.includes('prefers-reduced-motion'), 'expected reduced-motion CSS behavior');
  assert.ok(css.includes('--faint: #858184'), 'expected accessible faint text token');
  assert.ok(css.includes('@fontsource-variable/geist'), 'expected self-hosted Geist font import');
});

test('landing page uses authentic demo captures and a direct setup path', () => {
  assert.match(html, /\/demo\/failed-run-diff\.webp/);
  assert.match(html, /\/demo\/markdown-report\.webp/);
  assert.match(html, /docker compose up --build -d/);
  assert.match(html, /README\.md#quick-start/);
  assert.doesNotMatch(html, /Representative Backline CLI run|representative output|representative local history/);
  assert.ok(terminalCapture.size > 0, 'expected terminal demo capture to be committed');
  assert.ok(reportCapture.size > 0, 'expected Markdown report capture to be committed');
});

test('visible copy avoids typographic dash clutter', () => {
  assert.doesNotMatch(html, /[\u2014\u2013]/);
});
