import { Route, Routes } from 'react-router-dom'

function Shell({ view }: { view: string }) {
  return <main><h1>PokerNight</h1><p>{view} frontend shell</p></main>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Shell view="Home" />} />
      <Route path="/room/:code" element={<Shell view="Host" />} />
      <Route path="/room/:code/play" element={<Shell view="Player" />} />
      <Route path="/room/:code/tv" element={<Shell view="TV" />} />
    </Routes>
  )
}
