import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const [html, css] = await Promise.all([
  readFile(new URL('../index.html', import.meta.url), 'utf8'),
  readFile(new URL('../src/style.css', import.meta.url), 'utf8'),
]);

test('landing page exposes the verified product narrative', () => {
  for (const phrase of [
    'backline run',
    'backline history',
    'backline diff',
    'backline report',
    'QUEUED',
    'RUNNING',
    'PASSED',
    'FAILED',
    'exit 5',
    'PostgreSQL',
    'No hosted monitoring',
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

test('representative run output is labeled as representative', () => {
  assert.match(html, /representative output/);
  assert.match(html, /representative local history/);
  assert.match(html, /Representative Backline CLI run/);
  assert.match(html, /Regression detected/);
});

test('visible copy avoids typographic dash clutter', () => {
  assert.doesNotMatch(html, /[\u2014\u2013]/);
});
