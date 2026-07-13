import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('has no accessibility violations in the configured WCAG checks', async ({ page }) => {
  await page.goto('/');

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  const requiredRules = new Set([
    'aria-command-name',
    'button-name',
    'color-contrast',
    'focus-order-semantics',
    'link-name',
    'scrollable-region-focusable',
  ]);

  expect(results.violations.filter((violation) => requiredRules.has(violation.id))).toEqual([]);
  expect(results.violations).toEqual([]);
});
