import { expect, test } from '@playwright/test'

test('host route survives direct navigation, reload, and page resume', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Host name').fill('Mobile host')
  await page.getByLabel('Bots').fill('1')
  await page.getByRole('button', { name: 'Create room' }).click()
  await expect(page).toHaveURL(/\/room\/[A-Z0-9]{6}$/)
  const roomHeading = page.getByRole('heading', { name: /Room [A-Z0-9]{6}/ })
  await expect(roomHeading).toBeVisible()

  await page.reload()
  await expect(roomHeading).toBeVisible()
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })))
  await expect(page.getByText('Connected', { exact: true })).toBeVisible()
})
