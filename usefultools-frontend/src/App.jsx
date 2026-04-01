import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }       from './auth/AuthContext'
import ProtectedRoute         from './components/ProtectedRoute'
import LoginPage              from './pages/LoginPage/LoginPage'
import RegisterPage           from './pages/RegisterPage/RegisterPage'
import UpdatePasswordPage     from './pages/UpdatePasswordPage/UpdatePasswordPage'
import DashboardPage          from './pages/DashboardPage/DashboardPage'
import CalculatorPage         from './pages/CalculatorPage/CalculatorPage'
import NumberAnalyserPage     from './pages/NumberAnalyzerPage/NumberAnalyzerPage'
import PasswordVaultPage      from './pages/PasswordVaultPage/PasswordVaultPage'
import ProfilePage from './pages/ProfilePage/ProfilePage'
import UnitConverterPage from './pages/UnitConverterPage/UnitConverterPage'

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
          <Route path="/analyser" element={
            <ProtectedRoute><NumberAnalyserPage /></ProtectedRoute>
          } />
          <Route path="/vault" element={
            <ProtectedRoute><PasswordVaultPage /></ProtectedRoute>
          } />
          <Route path="/converter" element={
            <ProtectedRoute><UnitConverterPage /></ProtectedRoute>
          } />
          <Route path="/profile" element={
            <ProtectedRoute><ProfilePage /></ProtectedRoute>
          } />

          {/* ── Fallback ──────────────────────────────────────────────── */}
          <Route path="*" element={<Navigate to="/login" replace />} />

        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}