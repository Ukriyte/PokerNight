import { expect, test } from '@playwright/test'

test('room creation remains visually stable', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /Set the stakes/ })).toBeVisible()
  await expect(page).toHaveScreenshot('create-room.png', {
    fullPage: true,
    animations: 'disabled',
  })
})

test('public TV table remains visually stable at viewing distance', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'TV baseline uses the dedicated 1600×900 display size')
  await page.setViewportSize({ width: 1600, height: 900 })
  await page.goto('/')
  await page.getByLabel('Host name').fill('Visual host')
  await page.getByLabel('Bots').fill('2')
  await page.getByRole('button', { name: 'Create room' }).click()
  await expect(page).toHaveURL(/\/room\/[A-Z0-9]{6}$/)
  const roomCode = page.url().match(/\/room\/([A-Z0-9]{6})$/)?.[1]
  if (!roomCode) throw new Error('Room code was not present in the host URL')
  await page.goto(`/room/${roomCode}/tv`)
  await expect(page.getByRole('region', { name: 'Poker table' })).toBeVisible()
  await expect(page).toHaveScreenshot('tv-table.png', {
    animations: 'disabled',
    mask: [page.getByText(roomCode, { exact: false }), page.getByRole('status')],
    maxDiffPixels: 100,
  })
})
