import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider, useTheme } from './ThemeProvider'

function ThemeHarness() {
  const { theme, cardPackId, setTheme, setCardPack, applyRoomAppearance } = useTheme()
  return (
    <div>
      <span>{theme.name}</span>
      <span>{cardPackId}</span>
      <button onClick={() => setTheme('midnight')}>Use midnight</button>
      <button onClick={() => setTheme('broadcast')}>Use broadcast</button>
      <button onClick={() => setCardPack('four-color')}>Use four-colour cards</button>
      <button onClick={() => applyRoomAppearance({ themeId: 'midnight', cardPackId: 'four-color' })}>Apply room</button>
    </div>
  )
}

describe('ThemeProvider', () => {
  it('applies and persists the default theme', async () => {
    render(<ThemeProvider><ThemeHarness /></ThemeProvider>)

    expect(screen.getByText('Midnight Casino')).toBeInTheDocument()
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('midnight'))
    expect(document.documentElement.dataset.cardPack).toBe('classic')
    expect(window.localStorage.getItem('pokernight.theme')).toBe('midnight')
  })

  it('ignores an unknown stored theme', async () => {
    window.localStorage.setItem('pokernight.theme', 'missing-theme')
    render(<ThemeProvider><ThemeHarness /></ThemeProvider>)

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('midnight'))
  })

  it('exposes a typed theme setter', async () => {
    const user = userEvent.setup()
    render(<ThemeProvider><ThemeHarness /></ThemeProvider>)

    await user.click(screen.getByRole('button', { name: 'Use midnight' }))
    expect(window.localStorage.getItem('pokernight.theme')).toBe('midnight')
  })

  it('applies an authoritative room theme and card pack together', async () => {
    const user = userEvent.setup()
    render(<ThemeProvider><ThemeHarness /></ThemeProvider>)

    await user.click(screen.getByRole('button', { name: 'Use broadcast' }))
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('broadcast'))
    expect(document.documentElement.dataset.cardPack).toBe('four-color')

    await user.click(screen.getByRole('button', { name: 'Apply room' }))
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('midnight'))
    expect(document.documentElement.dataset.cardPack).toBe('four-color')
  })
})
