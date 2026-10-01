import { useState } from 'react'
import { publicGameStateFixture } from '../contracts/contractFixtures'
import { Banner, Button, CardPair, ConnectionBadge, Dialog, PlayingCard, TableBoard, TextField } from '../ui'

const sampleCards = {
  first: { rank: 'ACE' as const, suit: 'SPADES' as const },
  second: { rank: 'KING' as const, suit: 'HEARTS' as const },
}

export function ComponentGallery() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [revealed, setRevealed] = useState(false)
  return (
    <main className="gallery">
      <header className="gallery__header">
        <div>
          <p className="gallery__eyebrow">Internal component gallery</p>
          <h1>Card club kit</h1>
        </div>
        <ConnectionBadge status="connected" />
      </header>
      <p className="gallery__intro">Functional primitives for host screens, player phones, and the shared table. This route is development-only.</p>

      <section className="gallery__section">
        <h2>Actions</h2>
        <div className="gallery__row">
          <Button>Create room</Button>
          <Button variant="secondary">Start hand</Button>
          <Button variant="danger">Fold</Button>
          <Button disabled>Unavailable</Button>
          <Button busy>Connecting</Button>
          <Button variant="quiet" onClick={() => setDialogOpen(true)}>Open dialog</Button>
        </div>
      </section>

      <section className="gallery__section">
        <h2>Fields and feedback</h2>
        <div className="gallery__grid">
          <TextField label="Display name" hint="Shown to everyone at the table." defaultValue="Alice" />
          <TextField label="Starting chips" type="number" error="Enter at least 50 chips." defaultValue="20" />
          <Banner title="Connected">Live table updates are active.</Banner>
          <Banner tone="error" title="Session expired">Join the room again.</Banner>
        </div>
      </section>

      <section className="gallery__section">
        <h2>Private cards</h2>
        <div className="gallery__grid">
          <div className="gallery__sample"><h3>Waiting</h3><CardPair cards={null} revealed={false} /></div>
          <div className="gallery__sample"><h3>Atomic reveal</h3><CardPair cards={sampleCards} revealed={revealed} onReveal={() => setRevealed(true)} onHide={() => setRevealed(false)} /></div>
        </div>
      </section>

      <section className="gallery__section">
        <h2>Card packs</h2>
        <div className="gallery__row">
          <div className="gallery__sample"><h3>Classic</h3><PlayingCard card={sampleCards.second} presentation="face" packId="classic" /></div>
          <div className="gallery__sample"><h3>Four-colour</h3><PlayingCard card={{ rank: 'QUEEN', suit: 'DIAMONDS' }} presentation="face" packId="four-color" /></div>
          <div className="gallery__sample"><h3>Four-colour back</h3><PlayingCard presentation="back" packId="four-color" /></div>
        </div>
      </section>

      <section className="gallery__section">
        <h2>Table and seats</h2>
        <TableBoard
          players={publicGameStateFixture.players}
          communityCards={[
            { rank: 'TWO', suit: 'CLUBS' },
            { rank: 'SEVEN', suit: 'DIAMONDS' },
            { rank: 'QUEEN', suit: 'SPADES' },
          ]}
          pot={publicGameStateFixture.pot}
          currentPlayerId={publicGameStateFixture.currentPlayerId}
          botPlayerIds={['p2']}
        />
      </section>

      <Dialog open={dialogOpen} title="Leave this room?" onClose={() => setDialogOpen(false)}>
        <p>Your seat remains assigned until the host removes you.</p>
        <div className="gallery__row">
          <Button variant="secondary" onClick={() => setDialogOpen(false)}>Stay</Button>
          <Button variant="danger" onClick={() => setDialogOpen(false)}>Leave room</Button>
        </div>
      </Dialog>
    </main>
  )
}
