import { useEffect } from 'react'
import type { RoomAppearance } from '../appearance/types'
import { useTheme } from './ThemeProvider'

/** Applies server-authoritative room appearance after bootstrap and reconnect. */
export function useRoomAppearance(appearance: RoomAppearance | null | undefined): void {
  const { applyRoomAppearance } = useTheme()

  useEffect(() => {
    if (appearance) applyRoomAppearance(appearance)
  }, [appearance?.cardPackId, appearance?.themeId, applyRoomAppearance])
}
