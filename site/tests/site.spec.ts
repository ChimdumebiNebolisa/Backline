import { expect, test } from '@playwright/test';

test('renders the landing page and primary paths on desktop', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('Backline | API regression history');
  await expect(page.getByRole('heading', { name: 'Regression history for APIs that change.' })).toBeVisible();
  const terminalCapture = page.getByRole('img', { name: /failed broken-endpoint run and its diff/i });
  const reportCapture = page.getByRole('img', { name: /two passed and one failed check/i });

  await expect(terminalCapture).toBeVisible();
  await expect(terminalCapture).toHaveAttribute('src', '/demo/failed-run-diff.webp');
  await expect(reportCapture).toHaveAttribute('src', '/demo/markdown-report.webp');
  expect(await terminalCapture.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
  expect(await reportCapture.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
  await expect(page.locator('[aria-label="Start locally"]')).toContainText('docker compose up --build -d');
  await expect(page.getByRole('link', { name: /Open setup guide/ })).toHaveAttribute(
    'href',
    'https://github.com/ChimdumebiNebolisa/Backline/blob/main/README.md#quick-start',
  );
  await expect(page.getByRole('link', { name: 'GitHub' }).first()).toHaveAttribute(
    'href',
    'https://github.com/ChimdumebiNebolisa/Backline',
  );
});

test('keeps normal-motion reveal content visible before it settles', async ({ page }) => {
  await page.goto('/');

  const opacities = await page.locator('[data-reveal]').evaluateAll((elements) =>
    elements.map((element) => getComputedStyle(element).opacity),
  );

  expect(opacities).toContain('1');
  expect(opacities.every((opacity) => opacity === '1')).toBe(true);
});

test('supports mobile navigation and returns focus after Escape', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  const toggle = page.getByRole('button', { name: 'Toggle navigation' });
  const nav = page.getByRole('navigation', { name: 'Primary navigation' });

  await expect(toggle).toBeVisible();
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await expect(nav).toHaveClass(/is-open/);

  await page.keyboard.press('Escape');
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(toggle).toBeFocused();
});

test('does not overflow horizontally at the mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));

  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
});

test('honors reduced-motion preference', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const scrollBehavior = await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior);
  expect(scrollBehavior).toBe('auto');

  const revealState = await page.locator('[data-reveal]').evaluateAll((elements) =>
    elements.map((element) => {
      const style = getComputedStyle(element);
      return { opacity: style.opacity, transform: style.transform };
    }),
  );
  expect(revealState).not.toHaveLength(0);
  expect(revealState.every((state) => state.opacity === '1' && state.transform === 'none')).toBe(true);
});

test('matches the desktop visual baseline', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');

  await expect(page).toHaveScreenshot('landing-desktop.png', {
    animations: 'disabled',
    caret: 'hide',
    maxDiffPixelRatio: 0.05,
  });
});

test('matches the mobile visual baseline', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  await expect(page).toHaveScreenshot('landing-mobile.png', {
    animations: 'disabled',
    caret: 'hide',
    maxDiffPixelRatio: 0.05,
  });
});
