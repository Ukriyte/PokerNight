import { expect, test, type Browser, type Page } from '@playwright/test'

async function createRoom(page: Page, options: { bots?: number; maximumPlayers?: number; theme?: 'midnight' | 'broadcast' } = {}) {
  await page.goto('/')
  await page.getByLabel('Host name').fill('Host')
  await page.getByLabel('Maximum players').fill(String(options.maximumPlayers ?? 3))
  await page.getByLabel('Bots').fill(String(options.bots ?? 1))
  if (options.theme) await page.getByLabel('Table theme').selectOption(options.theme)
  await page.getByRole('button', { name: 'Create room' }).click()
  await expect(page).toHaveURL(/\/room\/[A-Z0-9]{6}$/)
  const roomCode = page.url().match(/\/room\/([A-Z0-9]{6})$/)?.[1]
  if (!roomCode) throw new Error('Room code was not present in the host URL')
  await expect(page.getByRole('heading', { name: `Room ${roomCode}` })).toBeVisible()
  return roomCode
}

test('host-selected appearance is restored for host, player, and TV views', async ({ page, browser }) => {
  const roomCode = await createRoom(page, { theme: 'broadcast' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'broadcast')
  await expect(page.locator('html')).toHaveAttribute('data-card-pack', 'four-color')
  await expect(page.locator('.table-board__felt')).toHaveCSS('background-image', /broadcast-felt\.jpg/)

  const player = await requestSeat(browser, roomCode, 'Alice')
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(player.page.locator('html')).toHaveAttribute('data-theme', 'broadcast')
  await expect(player.page.locator('html')).toHaveAttribute('data-card-pack', 'four-color')

  const tvContext = await browser.newContext()
  const tv = await tvContext.newPage()
  await tv.goto(`/room/${roomCode}/tv`)
  await expect(tv.locator('html')).toHaveAttribute('data-theme', 'broadcast')
  await expect(tv.locator('html')).toHaveAttribute('data-card-pack', 'four-color')
  await expect(tv.locator('.table-board__felt')).toHaveCSS('background-image', /broadcast-felt\.jpg/)

  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'broadcast')
  await player.context.close()
  await tvContext.close()
})

async function requestSeat(browser: Browser, roomCode: string, name: string) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`/room/${roomCode}/play`)
  await page.getByLabel('Your table name').fill(name)
  await page.getByRole('button', { name: 'Ask to join' }).click()
  await expect(page.getByRole('heading', { name: 'Waiting for the host' })).toBeVisible()
  return { context, page }
}

test('packaged room supports approval, private cards, refresh, history, TV, and another hand', async ({ page, browser }) => {
  const roomCode = await createRoom(page)
  const player = await requestSeat(browser, roomCode, 'Alice')

  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(player.page.getByRole('heading', { name: 'Alice' })).toBeVisible()

  const tvContext = await browser.newContext({ viewport: { width: 1600, height: 900 } })
  const tv = await tvContext.newPage()
  await tv.goto(`/room/${roomCode}/tv`)
  await expect(tv.getByRole('region', { name: 'Poker table' })).toBeVisible()
  await expect(tv.getByText('Your cards')).toHaveCount(0)
  await expect(tv.getByRole('button', { name: /Reveal/ })).toHaveCount(0)

  await page.getByRole('button', { name: 'Start first hand' }).click()
  const reveal = player.page.getByRole('button', { name: 'Reveal both cards' })
  await expect(reveal).toBeVisible()
  const cardBacks = player.page.locator('.player-private .playing-card--back')
  await expect(cardBacks).toHaveCount(2)
  await expect(cardBacks.first()).toHaveAttribute('src', '/cards/1B.svg')
  await reveal.click()
  const privateCards = player.page.locator('.player-private .card-pair__cards').getByRole('img')
  await expect(privateCards).toHaveCount(2)
  const firstDeal = await privateCards.evaluateAll(cards =>
    cards.map(card => card.getAttribute('aria-label') ?? card.getAttribute('alt')),
  )
  expect(firstDeal).toHaveLength(2)
  expect(firstDeal.every(label => label !== null && label !== 'Face-down playing card')).toBe(true)

  await player.page.reload()
  await expect(player.page.getByRole('heading', { name: 'Alice' })).toBeVisible()
  await player.page.getByRole('button', { name: 'Reveal both cards' }).click()
  await expect(privateCards).toHaveCount(2)
  expect(await privateCards.evaluateAll(cards =>
    cards.map(card => card.getAttribute('aria-label') ?? card.getAttribute('alt')),
  )).toEqual(firstDeal)

  const fold = player.page.getByRole('button', { name: 'Fold' })
  if (await fold.isVisible({ timeout: 10_000 }).catch(() => false)) await fold.click()
  await expect(page.getByRole('button', { name: 'Deal next hand' })).toBeVisible({ timeout: 20_000 })
  await expect(page.locator('.seat__cards').getByRole('img')).toHaveCount(4)
  await expect(tv.locator('.seat__cards').getByRole('img')).toHaveCount(4)
  await expect(page.locator('.history-list li').first()).toBeVisible()

  await page.getByRole('button', { name: 'Deal next hand' }).click()
  await expect(page.locator('.seat__cards').getByRole('img')).toHaveCount(0)
  await expect(player.page.getByRole('button', { name: 'Reveal both cards' })).toBeVisible()

  await player.context.close()
  await tvContext.close()
})

test('reject and kick revoke the affected browser credentials', async ({ page, browser }) => {
  const roomCode = await createRoom(page, { bots: 0, maximumPlayers: 3 })
  const rejected = await requestSeat(browser, roomCode, 'Rejected player')
  await expect(page.getByText('Rejected player', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Reject' }).click()
  await expect(rejected.page.getByText('Previous access ended')).toBeVisible()

  const kicked = await requestSeat(browser, roomCode, 'Kicked player')
  await expect(page.getByText('Kicked player', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(kicked.page.getByRole('heading', { name: 'Kicked player' })).toBeVisible()
  await page.getByRole('button', { name: 'Remove' }).click()
  await expect(kicked.page.getByText('Previous access ended')).toBeVisible()

  await rejected.context.close()
  await kicked.context.close()
})
