import { Navigate } from 'react-router-dom'
import { useAuth }   from '../auth/AuthContext'

/**
 * ProtectedRoute — a guard that blocks unauthenticated access to a route.
 *
 * HOW IT WORKS:
 * It reads isLoggedIn from AuthContext. If the user is not logged in, it
 * renders <Navigate to="/login" replace /> instead of the page content —
 * React Router immediately performs a client-side redirect to /login.
 * The protected page never renders, so there is no flash of content.
 *
 * The "replace" prop means the redirect replaces the current history entry
 * rather than pushing a new one. This prevents the back button from
 * bouncing the user straight back to the protected page they were redirected
 * away from.
 *
 * IMPORTANT:
 * This is client-side protection only — it controls what React renders.
 * The real security is AuthFilter on the backend, which returns 401 for
 * any /api/* request without a valid server session, regardless of what
 * the React client thinks.
 *
 * USAGE in App.jsx:
 *   <Route path="/dashboard" element={
 *     <ProtectedRoute>
 *       <DashboardPage />
 *     </ProtectedRoute>
 *   } />
 */
export default function ProtectedRoute({ children }) {
  const { isLoggedIn } = useAuth()

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />
  }

  // children is whatever component was passed between the tags,
  // e.g. <DashboardPage /> in the example above.
  return children
}
