import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { ThemeProvider } from './theme/ThemeProvider'

it('opens the room creation flow', () => {
  render(<ThemeProvider><MemoryRouter><App /></MemoryRouter></ThemeProvider>)
  expect(screen.getByRole('heading', { name: /set the stakes/i })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Create room' })).toBeInTheDocument()
})
