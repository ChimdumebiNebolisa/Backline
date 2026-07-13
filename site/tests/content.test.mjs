import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const [html, css, robots, sourceNote] = await Promise.all([
  readFile(new URL('../index.html', import.meta.url), 'utf8'),
  readFile(new URL('../src/style.css', import.meta.url), 'utf8'),
  readFile(new URL('../public/robots.txt', import.meta.url), 'utf8'),
  readFile(new URL('../public/demo/SOURCE.md', import.meta.url), 'utf8'),
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
    'rel="canonical" href="https://backline-site-xi.vercel.app/"',
    'https://github.com/ChimdumebiNebolisa/Backline',
  ]) {
    assert.ok(html.includes(phrase), `expected page to contain ${phrase}`);
  }
  assert.ok(css.includes('prefers-reduced-motion'), 'expected reduced-motion CSS behavior');
  assert.ok(css.includes('--faint: #858184'), 'expected accessible faint text token');
  assert.ok(css.includes('@fontsource-variable/geist'), 'expected self-hosted Geist font import');
});

test('landing page uses authentic demo text and an install-first setup path', () => {
  assert.match(html, /\.\/gradlew :apps:cli:installDist/);
  assert.match(html, /broken-endpoint \(Broken endpoint\) null -&gt; FAILED/);
  assert.match(html, /Expected status 200 but was 500/);
  assert.match(html, /README\.md#quick-start/);
  assert.doesNotMatch(html, /<img\b/i);
  assert.doesNotMatch(html, /\.webp/);
  assert.match(sourceNote, /GitHub Actions run 29216457678/);
  assert.match(sourceNote, /without fabrication/);
});

test('visible copy avoids typographic dash clutter', () => {
  assert.doesNotMatch(html, /[\u2014\u2013]/);
});

test('crawler policy allows the landing page to be indexed', () => {
  assert.equal(robots.replace(/\r\n/g, '\n'), 'User-agent: *\nAllow: /\n');
});
