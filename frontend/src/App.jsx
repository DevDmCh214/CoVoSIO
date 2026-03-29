import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Home from './pages/Home'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        {/* Placeholder routes — filled in later phases */}
        <Route
          path="/login"
          element={
            <div className="flex items-center justify-center h-screen text-gray-400 text-sm">
              Login — coming soon
            </div>
          }
        />
        <Route
          path="/register"
          element={
            <div className="flex items-center justify-center h-screen text-gray-400 text-sm">
              Register — coming soon
            </div>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}
