import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'

/**
 * AdminRoute — Sprint 17 RBAC.
 *
 * Wraps a route element and enforces admin-only access.
 *
 * - Unauthenticated  → redirect to /login
 * - Authenticated but not admin → redirect to /dashboard
 * - Authenticated admin → renders children
 *
 * Renders null while the redirect is in-flight so nothing flashes.
 */
export default function AdminRoute({ children }) {
  const { isLoggedIn, isAdmin } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isLoggedIn) {
      navigate('/login', { replace: true })
    } else if (!isAdmin) {
      navigate('/dashboard', { replace: true })
    }
  }, [isLoggedIn, isAdmin, navigate])

  if (!isLoggedIn || !isAdmin) return null
  return children
}
