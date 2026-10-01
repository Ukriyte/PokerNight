import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { CreateRoomRequest } from '../contracts/api'
import { createRoomRequestSchema } from '../contracts/schemas'
import { roomsApi } from '../api/roomsApi'
import { createBrowserSessionRepository } from '../session/sessionRepository'
import { ApiClientError } from '../api/httpClient'
import { Banner, Button, LiveRegion, TextField } from '../ui'
import { SelectField } from '../ui'
import { cardPacks } from '../cards/cardPacks'
import { getTheme } from '../theme/themes'
import { useTheme } from '../theme/ThemeProvider'

const defaults: CreateRoomRequest = {
  hostName: '', startingChips: 1000, smallBlind: 10, bigBlind: 20, maximumPlayers: 6, botCount: 0,
  themeId: 'midnight', cardPackId: 'classic',
}

export function CreateRoomPage() {
  const navigate = useNavigate()
  const { themes, setTheme, setCardPack } = useTheme()
  const [form, setForm] = useState(defaults)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const number = (key: keyof CreateRoomRequest) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm(current => ({ ...current, [key]: Number(event.target.value) }))
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const parsed = createRoomRequestSchema.safeParse(form)
    if (!parsed.success) {
      setFieldErrors(Object.fromEntries(parsed.error.issues.map(issue => {
        const field = String(issue.path[0])
        return [field, readableFieldError(field, issue.message)]
      })))
      return
    }
    setFieldErrors({})
    setError(null)
    setBusy(true)
    try {
      const created = await roomsApi.createRoom(parsed.data)
      createBrowserSessionRepository().save({ roomCode: created.roomCode, role: 'HOST', token: created.hostToken })
      navigate(created.hostUrl, { replace: true, state: { joinUrl: created.joinUrl } })
    } catch (cause) {
      setError(cause instanceof ApiClientError
        ? cause.detail
        : `The room may have been created, but setup did not finish.${cause instanceof Error ? ` ${cause.message}` : ''}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="create-page">
      <section className="create-intro">
        <p className="eyebrow">Neighborhood table · new room</p>
        <h1>Set the stakes.<br />Open the table.</h1>
        <p>The game runs on this server. Players only need your room link and a browser on the same network.</p>
      </section>
      <form className="create-form" onSubmit={event => void submit(event)} noValidate>
        <header><span className="form-suit" aria-hidden="true">♠</span><div><h2>Room setup</h2><p>You can add human players from the host desk after creation.</p></div></header>
        {error ? <Banner tone="error" title="Room was not confirmed">{error}</Banner> : null}
        <TextField label="Host name" autoComplete="name" maxLength={40} value={form.hostName} {...fieldError(fieldErrors.hostName)} onChange={event => setForm(current => ({ ...current, hostName: event.target.value }))} />
        <div className="form-grid">
          <TextField label="Starting chips" type="number" min={50} value={form.startingChips} {...fieldError(fieldErrors.startingChips)} onChange={number('startingChips')} />
          <TextField label="Maximum players" type="number" min={2} max={6} value={form.maximumPlayers} {...fieldError(fieldErrors.maximumPlayers)} onChange={number('maximumPlayers')} />
          <TextField label="Small blind" type="number" min={1} value={form.smallBlind} {...fieldError(fieldErrors.smallBlind)} onChange={number('smallBlind')} />
          <TextField label="Big blind" type="number" min={2} value={form.bigBlind} {...fieldError(fieldErrors.bigBlind)} onChange={number('bigBlind')} />
        </div>
        <TextField label="Bots" type="number" min={0} max={Math.max(0, form.maximumPlayers - 1)} value={form.botCount} hint="Bots occupy seats immediately. Leave at least one human seat." {...fieldError(fieldErrors.botCount)} onChange={number('botCount')} />
        <div className="form-grid">
          <SelectField
            label="Table theme"
            value={form.themeId}
            options={themes.map(theme => ({ value: theme.id, label: theme.name }))}
            onChange={event => {
              const themeId = event.target.value as CreateRoomRequest['themeId']
              const cardPackId = getTheme(themeId).cardPackId
              setForm(current => ({ ...current, themeId, cardPackId }))
              setTheme(themeId)
            }}
          />
          <SelectField
            label="Card style"
            value={form.cardPackId}
            options={Object.values(cardPacks).map(pack => ({ value: pack.id, label: pack.name }))}
            onChange={event => {
              const cardPackId = event.target.value as CreateRoomRequest['cardPackId']
              setForm(current => ({ ...current, cardPackId }))
              setCardPack(cardPackId)
            }}
          />
        </div>
        <Button type="submit" busy={busy}>Create room</Button>
        <LiveRegion message={busy ? 'Creating room' : ''} />
      </form>
    </main>
  )
}

function fieldError(error: string | undefined): { error?: string } {
  return error === undefined ? {} : { error }
}

function readableFieldError(field: string, fallback: string): string {
  if (field === 'hostName') return 'Enter a host name.'
  if (field === 'startingChips') return 'Starting chips must cover the big blind and be at least 50.'
  if (field === 'smallBlind') return 'Small blind must be positive and lower than the big blind.'
  if (field === 'bigBlind') return 'Big blind must be at least 2.'
  if (field === 'maximumPlayers') return 'Choose between 2 and 6 total players.'
  if (field === 'botCount') return 'Choose 0–5 bots and leave at least one human seat.'
  return fallback
}
