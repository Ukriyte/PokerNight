import type { ConnectionStatus } from '../room/roomState'

const labels: Record<ConnectionStatus, string> = {
  offline: 'Offline',
  connecting: 'Connecting…',
  connected: 'Connected',
  reconnecting: 'Reconnecting… Actions are paused.',
}

export function ConnectionBadge({ status }: { status: ConnectionStatus }) {
  return <span className={`connection connection--${status}`} role="status"><span aria-hidden="true" />{labels[status]}</span>
}
