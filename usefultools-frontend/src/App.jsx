import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider }   from './auth/AuthContext'
import ProtectedRoute     from './components/ProtectedRoute'
import LoginPage          from './pages/LoginPage/LoginPage'
import RegisterPage       from './pages/RegisterPage/RegisterPage'
import UpdatePasswordPage from './pages/UpdatePasswordPage/UpdatePasswordPage'
import DashboardPage      from './pages/DashboardPage/DashboardPage'

/**
 * App.jsx — declares every route in the application.
 *
 * KEY CONCEPTS:
 *
 * BrowserRouter:
 *   Enables client-side routing. When the user navigates to /dashboard,
 *   React handles it entirely — no request is sent to Tomcat. Tomcat only
 *   ever receives /api/* calls. This is what makes React a "Single Page App".
 *
 * AuthProvider:
 *   Wraps all routes so that every page component can call useAuth() to
 *   read the logged-in username or call login()/logout(). It must be inside
 *   BrowserRouter (because login/logout trigger navigations).
 *
 * ProtectedRoute:
 *   A guard component — if the user is not logged in it redirects to /login
 *   before the protected page ever renders. The real security is AuthFilter
 *   on the backend; this is just good UX.
 *
 * Route order:
 *   React Router matches routes top-to-bottom and stops at the first match.
 *   The catch-all redirect at the bottom only fires if no other route matched.
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>

          {/* ── Public routes ─────────────────────────────────────────── */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* ── Protected routes ──────────────────────────────────────── */}
          <Route
            path="/update-password"
            element={
              <ProtectedRoute>
                <UpdatePasswordPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />

          {/* ── Fallback: redirect root and unknown paths to /login ────── */}
          <Route path="*" element={<Navigate to="/login" replace />} />

        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
