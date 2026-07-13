import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('has no accessibility violations in the configured WCAG checks', async ({ page }) => {
  await page.goto('/');

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(results.violations).toEqual([]);
});
