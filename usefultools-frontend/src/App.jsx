import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }   from './auth/AuthContext'
import ProtectedRoute     from './components/ProtectedRoute'
import LoginPage          from './pages/LoginPage/LoginPage'
import RegisterPage       from './pages/RegisterPage/RegisterPage'
import UpdatePasswordPage from './pages/UpdatePasswordPage/UpdatePasswordPage'
import DashboardPage      from './pages/DashboardPage/DashboardPage'
import CalculatorPage     from './pages/CalculatorPage/CalculatorPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>

          {/* ── Public routes ─────────────────────────────────────────── */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* ── Protected routes ──────────────────────────────────────── */}
          <Route path="/update-password" element={
            <ProtectedRoute><UpdatePasswordPage /></ProtectedRoute>
          } />
          <Route path="/dashboard" element={
            <ProtectedRoute><DashboardPage /></ProtectedRoute>
          } />
          <Route path="/calculator" element={
            <ProtectedRoute><CalculatorPage /></ProtectedRoute>
          } />

          {/* ── Fallback ──────────────────────────────────────────────── */}
          <Route path="*" element={<Navigate to="/login" replace />} />

        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
