import { useCallback, useEffect, useState } from 'react'
import { fetchEmoteCategories } from '../api/apiClient'
import { useAuth } from '../auth/useAuth'

// The strict endpoint distinguishes disabled, failed storage and expired sessions.
// No generic tool-status default or administrator bypass authorizes this page.
export default function useEmoteAccess() {
  const { username, role } = useAuth()
  const identity = JSON.stringify([username, role])
  const [state, setState] = useState({ identity: null, status: 'checking' })
  const [revision, setRevision] = useState(0)
  const refresh = useCallback(() => setRevision(n => n + 1), [])
  useEffect(() => {
    let alive = true, controller, running = false
    const check = async (invalidate = false) => {
      if (!alive || !username || (running && !invalidate)) return
      if (invalidate) { controller?.abort(); setState({ identity, status: 'checking' }) }
      running = true
      const current = new AbortController(); controller = current
      try {
        const { status, data } = await fetchEmoteCategories({ type: 'all' }, current.signal)
        if (alive && !current.signal.aborted) setState({ identity, status: status === 200 && data?.success ? 'enabled' : status === 403 ? 'disabled' : status === 401 ? 'expired' : 'unavailable' })
      } catch {
        if (alive && !current.signal.aborted) setState({ identity, status: navigator.onLine ? 'unavailable' : 'offline' })
      } finally { if (controller === current) running = false }
    }
    queueMicrotask(() => check(true))
    const changed = () => check(true)
    const offline = () => { controller?.abort(); setState({ identity, status: 'offline' }) }
    const storage = event => { if (event.key === 'emote-availability') changed() }
    const timer = setInterval(() => check(), 15000)
    window.addEventListener('offline', offline); window.addEventListener('focus', changed); window.addEventListener('online', changed)
    window.addEventListener('emote-availability', changed); window.addEventListener('storage', storage)
    return () => {
      alive = false; controller?.abort(); clearInterval(timer)
      window.removeEventListener('offline', offline); window.removeEventListener('focus', changed); window.removeEventListener('online', changed)
      window.removeEventListener('emote-availability', changed); window.removeEventListener('storage', storage)
    }
  }, [identity, username, revision])
  return { status: state.identity === identity ? state.status : 'checking', refresh, identity }
}
