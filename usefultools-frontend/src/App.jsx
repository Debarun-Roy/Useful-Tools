import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }       from './auth/AuthContext'
import ProtectedRoute         from './components/ProtectedRoute'
import AdminRoute             from './components/AdminRoute/AdminRoute'
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
import ImageToolsPage         from './pages/ImageToolsPage/ImageToolsPage'
import DevUtilsPage           from './pages/DevUtilsPage/DevUtilsPage'
import TimeUtilsPage          from './pages/TimeUtilsPage/TimeUtilsPage'
import AdminPage              from './pages/AdminPage/AdminPage'
import FeedbackModal          from './components/FeedbackModal/FeedbackModal'

console.log(AuthProvider) // Ensure AuthProvider is included in the bundle for the unauthorized handler to work 

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
            <Route path="/image-tools" element={
              <ProtectedRoute><ImageToolsPage /></ProtectedRoute>
            } />
            <Route path="/dev-utils" element={
              <ProtectedRoute><DevUtilsPage /></ProtectedRoute>
            } />
            <Route path="/time-utils" element={
              <ProtectedRoute><TimeUtilsPage /></ProtectedRoute>
            } />

            {/*
              Sprint 17: Admin Panel — only accessible to users with role=admin.
              AdminRoute redirects non-admins to /dashboard and unauthenticated
              users to /login, so ProtectedRoute is not needed as an outer wrapper.
            */}
            <Route path="/admin" element={
              <AdminRoute><AdminPage /></AdminRoute>
            } />

            {/* ── Fallback ──────────────────────────────────────────────── */}
            <Route path="*" element={<Navigate to="/login" replace />} />

          </Routes>

          <FeedbackModal />

        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
