import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const [html, css, robots, sourceNote] = await Promise.all([
  readFile(new URL('../index.html', import.meta.url), 'utf8'),
  readFile(new URL('../src/style.css', import.meta.url), 'utf8'),
  readFile(new URL('../public/robots.txt', import.meta.url), 'utf8'),
  readFile(new URL('../public/demo/SOURCE.md', import.meta.url), 'utf8'),
]);

test('landing page presents the rollout verifier contract', () => {
  for (const phrase of [
    'backline verify',
    'Mixed-version compatibility',
    'Rollback compatibility [RAW]',
    'base after transition',
    'candidate before coexistence',
    'base with candidate running',
    'ROLLBACK_SCENARIO_FAILED',
    'RAW',
    'PREPARED',
    'A pass is bounded by configured workload coverage.',
    'Backline is not a sandbox',
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
    'rel="canonical" href="https://backline-site-xi.vercel.app/"',
    'https://github.com/ChimdumebiNebolisa/Backline',
    'rel="icon"',
    'property="og:image"',
  ]) {
    assert.ok(html.includes(phrase), `expected page to contain ${phrase}`);
  }
  assert.ok(css.includes('prefers-reduced-motion'), 'expected reduced-motion CSS behavior');
  assert.ok(css.includes('--faint: #858184'), 'expected accessible faint text token');
  assert.ok(css.includes('@fontsource-variable/geist'), 'expected self-hosted Geist font import');
});

test('landing page uses observed demo evidence and current install path', () => {
  assert.match(html, /go build -o backline \.\/cmd\/backline/);
  assert.match(html, /The base revision failed against state left by candidate cutover and traffic\./);
  assert.match(html, /base reads candidate traffic/);
  assert.match(html, /README\.md#quick-start/);
  assert.doesNotMatch(html, /\.\/gradlew/);
  assert.doesNotMatch(html, /Picocli|Spring Boot|Swagger|API regression ledger/);
  assert.match(sourceNote, /2026-09-02/);
  assert.match(sourceNote, /safe.*mixed-failure.*rollback-failure/s);
});

test('visible copy avoids typographic dash clutter', () => {
  assert.doesNotMatch(html, /[\u2014\u2013]/);
});

test('crawler policy allows the landing page to be indexed', () => {
  assert.equal(robots.replace(/\r\n/g, '\n'), 'User-agent: *\nAllow: /\n');
});
