import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Suspense, lazy }     from 'react'
import { AuthProvider }       from './auth/AuthContext'
import ProtectedRoute         from './components/ProtectedRoute'
import AdminRoute             from './components/AdminRoute/AdminRoute'
import { ThemeProvider }      from './theme/ThemeContext'
import FeedbackModal          from './components/FeedbackModal/FeedbackModal'

// ── Public routes — kept eager so the login screen renders instantly ───────
import LoginPage          from './pages/LoginPage/LoginPage'
import RegisterPage       from './pages/RegisterPage/RegisterPage'
import UpdatePasswordPage from './pages/UpdatePasswordPage/UpdatePasswordPage'

// ── Authenticated pages — lazy loaded (each becomes a separate Vite chunk) ─
// Sprint 24: Code-splitting. Vite 8 automatically creates one JS chunk per
// dynamic import, so the initial bundle only contains the shell, auth, and
// public pages. Each tool page is fetched on first navigation to that route.
const DashboardPage            = lazy(() => import('./pages/DashboardPage/DashboardPage'))
const CalculatorPage           = lazy(() => import('./pages/CalculatorPage/CalculatorPage'))
const NumberAnalyserPage       = lazy(() => import('./pages/NumberAnalyzerPage/NumberAnalyzerPage'))
const PasswordVaultPage        = lazy(() => import('./pages/PasswordVaultPage/PasswordVaultPage'))
const ProfilePage              = lazy(() => import('./pages/ProfilePage/ProfilePage'))
const UnitConverterPage        = lazy(() => import('./pages/UnitConverterPage/UnitConverterPage'))
const TextUtilitiesPage        = lazy(() => import('./pages/TextUtilitiesPage/TextUtilitiesPage'))
const EncodingDecodingPage     = lazy(() => import('./pages/EncodingDecodingPage/EncodingDecodingPage'))
const CodeUtilitiesPage        = lazy(() => import('./pages/CodeUtilitiesPage/CodeUtilitiesPage'))
const WebDevHelpersPage        = lazy(() => import('./pages/WebDevHelpersPage/WebDevHelpersPage'))
const ImageToolsPage           = lazy(() => import('./pages/ImageToolsPage/ImageToolsPage'))
const DevUtilsPage             = lazy(() => import('./pages/DevUtilsPage/DevUtilsPage'))
const TimeUtilsPage            = lazy(() => import('./pages/TimeUtilsPage/TimeUtilsPage'))
const AdminPage                = lazy(() => import('./pages/AdminPage/AdminPage'))
const ResponseFormatterPage    = lazy(() => import('./pages/ResponseFormatterPage/ResponseFormatterPage'))
const RegexBuilderPage         = lazy(() => import('./pages/RegexBuilderPage/RegexBuilderPage'))
const DataVisualizationPage    = lazy(() => import('./pages/DataVisualizationPage/DataVisualizationPage'))
const MarkdownConverterPage    = lazy(() => import('./pages/MarkdownConverterPage/MarkdownConverterPage'))
const ColorToolsPage           = lazy(() => import('./pages/ColorToolsPage/ColorToolsPage'))
const PlaceholderGeneratorPage = lazy(() => import('./pages/PlaceholderGeneratorPage/PlaceholderGeneratorPage'))
const SQLFormatterPage         = lazy(() => import('./pages/SQLFormatterPage/SQLFormatterPage'))
const BulkConverterPage        = lazy(() => import('./pages/BulkConverterPage/BulkConverterPage'))

// ── Suspense fallback — shown while a lazy chunk is downloading ────────────
function PageLoader() {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', background: 'var(--clr-bg)',
      color: 'var(--clr-text-muted)', fontFamily: 'inherit', fontSize: '0.875rem',
      gap: '0.5rem',
    }}>
      <span style={{
        display: 'inline-block', width: 16, height: 16, border: '2px solid currentColor',
        borderTopColor: 'transparent', borderRadius: '50%',
        animation: 'spin 0.6s linear infinite',
      }} />
      Loading…
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  )
}

//console.log(AuthProvider) // Ensure AuthProvider is included in the bundle for the unauthorized handler to work

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ThemeProvider>
          <Suspense fallback={<PageLoader />}>
            <Routes>

              {/* ── Public routes ───────────────────────────────────────── */}
              <Route path="/login"    element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              {/* ── Protected routes ────────────────────────────────────── */}
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
              <Route path="/formatter" element={
                <ProtectedRoute><ResponseFormatterPage /></ProtectedRoute>
              } />
              <Route path="/regex-builder" element={
                <ProtectedRoute><RegexBuilderPage /></ProtectedRoute>
              } />
              <Route path="/data-viz" element={
                <ProtectedRoute><DataVisualizationPage /></ProtectedRoute>
              } />
              <Route path="/markdown" element={
                <ProtectedRoute><MarkdownConverterPage /></ProtectedRoute>
              } />
              <Route path="/color-tools" element={
                <ProtectedRoute><ColorToolsPage /></ProtectedRoute>
              } />
              <Route path="/placeholder" element={
                <ProtectedRoute><PlaceholderGeneratorPage /></ProtectedRoute>
              } />
              <Route path="/sql-formatter" element={
                <ProtectedRoute><SQLFormatterPage /></ProtectedRoute>
              } />
              <Route path="/bulk-converter" element={
                <ProtectedRoute><BulkConverterPage /></ProtectedRoute>
              } />

              {/*
                Sprint 17: Admin Panel — only accessible to users with role=admin.
                AdminRoute redirects non-admins to /dashboard and unauthenticated
                users to /login, so ProtectedRoute is not needed as an outer wrapper.
              */}
              <Route path="/admin" element={
                <AdminRoute><AdminPage /></AdminRoute>
              } />

              {/* ── Fallback ─────────────────────────────────────────────── */}
              <Route path="*" element={<Navigate to="/login" replace />} />

            </Routes>
          </Suspense>

          <FeedbackModal />

        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
