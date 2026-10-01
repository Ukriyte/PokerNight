import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Dialog } from './Dialog'

function DialogHarness() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button onClick={() => setOpen(true)}>Open settings</button>
      <Dialog open={open} title="Table settings" onClose={() => setOpen(false)}>
        <button>First action</button>
        <button>Last action</button>
      </Dialog>
    </>
  )
}

describe('Dialog', () => {
  it('moves focus inside, closes on Escape, and restores trigger focus', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)
    const trigger = screen.getByRole('button', { name: 'Open settings' })
    await user.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Table settings' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
