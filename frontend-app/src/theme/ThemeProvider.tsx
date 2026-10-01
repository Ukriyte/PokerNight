import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { CardPackId, RoomAppearance } from '../appearance/types'
import {
  availableThemes,
  defaultThemeId,
  getTheme,
  isThemeId,
  type PokerTheme,
  type ThemeId,
} from './themes'

const THEME_STORAGE_KEY = 'pokernight.theme'
const CARD_PACK_STORAGE_KEY = 'pokernight.cardPack'

interface ThemeContextValue {
  readonly theme: PokerTheme
  readonly themeId: ThemeId
  readonly cardPackId: CardPackId
  readonly themes: readonly PokerTheme[]
  setTheme(themeId: ThemeId): void
  setCardPack(cardPackId: CardPackId): void
  applyRoomAppearance(appearance: RoomAppearance): void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readStoredTheme(): ThemeId {
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return isThemeId(storedTheme) ? storedTheme : defaultThemeId
  } catch {
    return defaultThemeId
  }
}

function storeTheme(themeId: ThemeId): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, themeId)
  } catch {
    // A blocked storage API must not prevent the table from rendering.
  }
}

function readStoredCardPack(fallback: CardPackId): CardPackId {
  try {
    const value = window.localStorage.getItem(CARD_PACK_STORAGE_KEY)
    return value === 'classic' || value === 'four-color' ? value : fallback
  } catch {
    return fallback
  }
}

function storeCardPack(cardPackId: CardPackId): void {
  try { window.localStorage.setItem(CARD_PACK_STORAGE_KEY, cardPackId) } catch { /* optional preference */ }
}

export function ThemeProvider({ children, initialThemeId }: {
  children: ReactNode
  initialThemeId?: ThemeId
}) {
  const [themeId, setThemeId] = useState<ThemeId>(() => initialThemeId ?? readStoredTheme())
  const theme = getTheme(themeId)
  const [cardPackId, setCardPackId] = useState<CardPackId>(() => readStoredCardPack(theme.cardPackId))

  const setTheme = useCallback((nextThemeId: ThemeId) => {
    setThemeId(nextThemeId)
    setCardPackId(getTheme(nextThemeId).cardPackId)
  }, [])

  const applyRoomAppearance = useCallback((appearance: RoomAppearance) => {
    setThemeId(appearance.themeId)
    setCardPackId(appearance.cardPackId)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = themeId
    document.documentElement.dataset.cardPack = cardPackId
    document.documentElement.style.colorScheme = theme.colorScheme
    storeTheme(themeId)
    storeCardPack(cardPackId)
  }, [cardPackId, theme.colorScheme, themeId])

  const value = useMemo<ThemeContextValue>(() => ({
    theme,
    themeId,
    cardPackId,
    themes: availableThemes,
    setTheme,
    setCardPack: setCardPackId,
    applyRoomAppearance,
  }), [applyRoomAppearance, cardPackId, setTheme, theme, themeId])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}

export function useCardPackId(): CardPackId {
  return useContext(ThemeContext)?.cardPackId ?? 'classic'
}
