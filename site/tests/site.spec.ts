import { expect, test } from '@playwright/test';

test('renders the landing page and primary paths on desktop', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('Backline | API regression history');
  await expect(page.getByRole('heading', { name: 'Regression history for APIs that change.' })).toBeVisible();
  await expect(page.getByLabel('Representative Backline CLI run')).toBeVisible();
  await expect(page.getByText('GET /users/{id}', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('link', { name: /Start with the CLI/ })).toHaveAttribute('href', '#quick-start');
  await expect(page.getByRole('link', { name: 'GitHub' }).first()).toHaveAttribute(
    'href',
    'https://github.com/ChimdumebiNebolisa/Backline',
  );
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

test('honors reduced-motion preference', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');

  const scrollBehavior = await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior);
  expect(scrollBehavior).toBe('auto');
});
