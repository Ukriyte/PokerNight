import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { roomsApi, type RoomsApi } from '../api/roomsApi'
import { Button } from '../ui'

export function JoinAccess({ roomCode, joinPath, api = roomsApi }: { roomCode: string; joinPath: string; api?: RoomsApi }) {
  const [joinUrl, setJoinUrl] = useState<string | null>(null)
  const [lanUnavailable, setLanUnavailable] = useState(false)
  const [qr, setQr] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    const abort = new AbortController()
    const direct = new URL(joinPath, window.location.origin)
    if (!isLocalhost(direct.hostname)) {
      setJoinUrl(direct.toString())
      setLanUnavailable(false)
      return () => abort.abort()
    }
    setJoinUrl(null)
    void api.getNetworkInfo({ signal: abort.signal }).then(info => {
      if (abort.signal.aborted) return
      setJoinUrl(new URL(joinPath, info.origin).toString())
      setLanUnavailable(!info.lanAvailable)
    }).catch(() => {
      if (abort.signal.aborted) return
      setJoinUrl(direct.toString())
      setLanUnavailable(true)
    })
    return () => abort.abort()
  }, [api, joinPath])

  useEffect(() => {
    let live = true
    if (!joinUrl) {
      setQr(null)
      return () => { live = false }
    }
    void QRCode.toDataURL(joinUrl, { margin: 1, width: 240, errorCorrectionLevel: 'M' })
      .then(value => { if (live) setQr(value) })
    return () => { live = false }
  }, [joinUrl])

  const copy = async () => {
    if (!joinUrl) return
    await navigator.clipboard.writeText(joinUrl)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  return (
    <section className="host-panel join-access" aria-labelledby="join-title">
      <div>
        <p className="eyebrow">Room {roomCode}</p>
        <h2 id="join-title">Invite the table</h2>
        <p>Players open this address, enter a name, and wait for your approval.</p>
        {lanUnavailable ? <p className="join-access__warning">A Wi-Fi address was not detected. Connect the Mac to the same Wi-Fi or hotspot as the phones.</p> : null}
        {joinUrl ? <a className="join-access__link" href={joinUrl}>{joinUrl}</a> : <p className="join-access__resolving">Finding this Mac’s Wi-Fi address…</p>}
        <Button type="button" variant="secondary" disabled={!joinUrl} onClick={() => void copy()}>{copied ? 'Copied' : 'Copy join link'}</Button>
      </div>
      {qr ? <img className="join-access__qr" src={qr} alt={`Scannable Wi-Fi QR code for room ${roomCode}`} /> : <div className="join-access__qr-placeholder">Preparing Wi-Fi QR…</div>}
    </section>
  )
}

function isLocalhost(hostname: string): boolean {
  return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(hostname)
}
