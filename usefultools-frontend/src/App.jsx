import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }       from './auth/AuthContext'
import ProtectedRoute         from './components/ProtectedRoute'
import { ThemeProvider }      from './theme/ThemeContext'
import LoginPage              from './pages/LoginPage/LoginPage'
import RegisterPage           from './pages/RegisterPage/RegisterPage'
import UpdatePasswordPage     from './pages/UpdatePasswordPage/UpdatePasswordPage'
import DashboardPage          from './pages/DashboardPage/DashboardPage'
import CalculatorPage         from './pages/CalculatorPage/CalculatorPage'
import NumberAnalyserPage     from './pages/NumberAnalyzerPage/NumberAnalyzerPage'
import PasswordVaultPage      from './pages/PasswordVaultPage/PasswordVaultPage'
import ProfilePage            from './pages/ProfilePage/ProfilePage'
import UnitConverterPage      from './pages/UnitConverterPage/UnitConverterPage'
import TextUtilitiesPage      from './pages/TextUtilitiesPage/TextUtilitiesPage'
import EncodingDecodingPage   from './pages/EncodingDecodingPage/EncodingDecodingPage'
import CodeUtilitiesPage      from './pages/CodeUtilitiesPage/CodeUtilitiesPage'
import WebDevHelpersPage      from './pages/WebDevHelpersPage/WebDevHelpersPage'
import FeedbackModal          from './components/FeedbackModal/FeedbackModal'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ThemeProvider>
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
            <Route path="/text-utils" element={
              <ProtectedRoute><TextUtilitiesPage /></ProtectedRoute>
            } />
            <Route path="/encoding" element={
              <ProtectedRoute><EncodingDecodingPage /></ProtectedRoute>
            } />
            <Route path="/code-utils" element={
              <ProtectedRoute><CodeUtilitiesPage /></ProtectedRoute>
            } />
            <Route path="/web-dev" element={
              <ProtectedRoute><WebDevHelpersPage /></ProtectedRoute>
            } />

            {/* ── Fallback ──────────────────────────────────────────────── */}
            <Route path="*" element={<Navigate to="/login" replace />} />

          </Routes>

          {/*
            FeedbackModal renders a floating button that is always present
            in the DOM for authenticated users. It checks isLoggedIn internally
            and returns null for unauthenticated sessions, so it is safe to
            mount it here outside the route tree — it will not appear on the
            login or register pages.
          */}
          <FeedbackModal />

        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
