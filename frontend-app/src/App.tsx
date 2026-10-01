import { Route, Routes } from 'react-router-dom'
import { ComponentGallery } from './pages/ComponentGallery'
import { CreateRoomPage } from './pages/CreateRoomPage'
import { HostRoomPage } from './pages/HostRoomPage'
import { PlayerRoomPage } from './pages/PlayerRoomPage'
import { TvRoomPage } from './pages/TvRoomPage'

export default function App() {
  return (
    <Routes>
      {import.meta.env.DEV ? <Route path="/_components" element={<ComponentGallery />} /> : null}
      <Route path="/" element={<CreateRoomPage />} />
      <Route path="/room/:roomCode" element={<HostRoomPage />} />
      <Route path="/room/:roomCode/play" element={<PlayerRoomPage />} />
      <Route path="/room/:roomCode/tv" element={<TvRoomPage />} />
      <Route path="*" element={<CreateRoomPage />} />
    </Routes>
  )
}
