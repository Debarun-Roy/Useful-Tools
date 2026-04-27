import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import {
  logoutUser,
  fetchAdminUsers,
  updateUserRole,
  deleteAdminUser,
  fetchAdminToolToggles,
  updateToolToggle,
} from '../../api/apiClient'
import styles from './AdminPage.module.css'
import AnalyticsTab from './AnalyticsTab'

/*
 * AdminPage — Sprint 17 RBAC.
 *
 * Two tabs:
 *   Users       — list all registered users, change roles, delete accounts
 *   Tool Toggles — enable / disable each tool globally
 *
 * Protected by AdminRoute in App.jsx.
 * All API calls require role=admin in the session (enforced by AdminFilter).
 */

// ── Tool metadata (for display in the toggles tab) ──────────────────────────

const TOOL_META = {
  '/calculator':  { icon: '🧮', label: 'Calculator' },
  '/analyser':    { icon: '🔢', label: 'Number Analyser' },
  '/vault':       { icon: '🔐', label: 'Password Vault' },
  '/converter':   { icon: '🔄', label: 'Unit Converter' },
  '/text-utils':  { icon: '📝', label: 'Text Utilities' },
  '/encoding':    { icon: '🔧', label: 'Encoding & Decoding' },
  '/code-utils':  { icon: '💻', label: 'Code Utilities' },
  '/web-dev':     { icon: '🛠️', label: 'Web Dev Helpers' },
  '/image-tools': { icon: '🖼️', label: 'Image Tools' },
  '/dev-utils':   { icon: '🧑‍💻', label: 'Dev Utilities' },
  '/time-utils':  { icon: '🕐', label: 'Time Utilities' },
}

// ── Role badge ──────────────────────────────────────────────────────────────

function RoleBadge({ role }) {
  const cls = role === 'admin' ? styles.badgeAdmin : styles.badgeUser
  return <span className={cls}>{role}</span>
}

// ── Toggle switch ───────────────────────────────────────────────────────────

function ToggleSwitch({ checked, onChange, disabled, id }) {
  return (
    <label className={`${styles.toggle} ${disabled ? styles.toggleDisabled : ''}`} htmlFor={id}>
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={e => !disabled && onChange(e.target.checked)}
        disabled={disabled}
      />
      <span className={styles.toggleSlider} />
    </label>
  )
}

// ── Inline toast ─────────────────────────────────────────────────────────────

function Toast({ message, type = 'success', onClose }) {
  useEffect(() => {
    const t = setTimeout(onClose, 3500)
    return () => clearTimeout(t)
  }, [onClose])

  return (
    <div className={type === 'error' ? styles.toastError : styles.toastSuccess}>
      {message}
      <button className={styles.toastClose} onClick={onClose}>×</button>
    </div>
  )
}

// ── Users tab ────────────────────────────────────────────────────────────────

function UsersTab({ currentUsername }) {
  const [users,    setUsers]    = useState([])
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const [query,    setQuery]    = useState('')
  const [toast,    setToast]    = useState(null)
  const [deleting, setDeleting] = useState(null)   // username pending delete confirmation
  const [working,  setWorking]  = useState(null)   // username currently being updated

  function showToast(message, type = 'success') {
    setToast({ message, type })
  }

  async function loadUsers() {
    setLoading(true)
    setError(null)
    try {
      const { data } = await fetchAdminUsers()
      if (data?.success) setUsers(data.data.users)
      else setError(data?.message || 'Failed to load users')
    } catch {
      setError('Network error — could not load users')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadUsers() }, [])

  async function handleRoleChange(username, newRole) {
    setWorking(username)
    try {
      const { data } = await updateUserRole(username, newRole)
      if (data?.success) {
        setUsers(prev => prev.map(u =>
          u.username === username ? { ...u, role: newRole } : u
        ))
        showToast(`${username} is now ${newRole}`)
      } else {
        showToast(data?.message || 'Could not update role', 'error')
      }
    } catch {
      showToast('Network error', 'error')
    } finally {
      setWorking(null)
    }
  }

  async function handleDelete(username) {
    setDeleting(null)
    setWorking(username)
    try {
      const { data } = await deleteAdminUser(username)
      if (data?.success) {
        setUsers(prev => prev.filter(u => u.username !== username))
        showToast(`${username} deleted`)
      } else {
        showToast(data?.message || 'Could not delete user', 'error')
      }
    } catch {
      showToast('Network error', 'error')
    } finally {
      setWorking(null)
    }
  }

  const filtered = useMemo(() =>
    query.trim()
      ? users.filter(u => u.username.toLowerCase().includes(query.toLowerCase()))
      : users,
    [users, query]
  )

  return (
    <div className={styles.tabPanel}>
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Confirm delete dialog */}
      {deleting && (
        <div className={styles.confirmOverlay}>
          <div className={styles.confirmDialog}>
            <h3 className={styles.confirmTitle}>Delete user?</h3>
            <p className={styles.confirmBody}>
              This will permanently delete <strong>{deleting}</strong> and all their
              data — vault entries, activity history, favorites. This cannot be undone.
            </p>
            <div className={styles.confirmActions}>
              <button className={styles.cancelBtn} onClick={() => setDeleting(null)}>
                Cancel
              </button>
              <button
                className={styles.dangerBtn}
                onClick={() => handleDelete(deleting)}
              >
                Delete permanently
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Search */}
      <div className={styles.searchRow}>
        <input
          type="search"
          className={styles.searchInput}
          placeholder="Search users…"
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
        <span className={styles.userCount}>
          {filtered.length} of {users.length} user{users.length !== 1 ? 's' : ''}
        </span>
      </div>

      {loading && (
        <div className={styles.loadingRow}>Loading users…</div>
      )}

      {error && !loading && (
        <div className={styles.errorBanner}>{error}</div>
      )}

      {!loading && !error && (
        <div className={styles.tableWrap}>
          <table className={styles.usersTable}>
            <thead>
              <tr>
                <th>Username</th>
                <th>Role</th>
                <th>Joined</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={4} className={styles.emptyCell}>
                    {query ? 'No users match that search.' : 'No users found.'}
                  </td>
                </tr>
              ) : filtered.map(user => {
                const isSelf  = user.username === currentUsername
                const busy    = working === user.username
                return (
                  <tr key={user.username} className={isSelf ? styles.selfRow : ''}>
                    <td className={styles.usernameCell}>
                      {user.username}
                      {isSelf && <span className={styles.selfTag}>you</span>}
                    </td>
                    <td>
                      <RoleBadge role={user.role} />
                    </td>
                    <td className={styles.dateCell}>
                      {user.createdDate !== 'Unknown'
                        ? new Date(user.createdDate).toLocaleDateString('en-GB', {
                            day: 'numeric', month: 'short', year: 'numeric',
                          })
                        : '—'
                      }
                    </td>
                    <td>
                      <div className={styles.actionCell}>
                        {/* Role selector */}
                        <select
                          className={styles.roleSelect}
                          value={user.role}
                          onChange={e => handleRoleChange(user.username, e.target.value)}
                          disabled={isSelf || busy}
                          title={isSelf ? 'Cannot change your own role' : 'Change role'}
                        >
                          <option value="user">user</option>
                          <option value="admin">admin</option>
                        </select>

                        {/* Delete button */}
                        <button
                          className={styles.deleteBtn}
                          onClick={() => setDeleting(user.username)}
                          disabled={isSelf || busy}
                          title={isSelf ? 'Cannot delete yourself' : `Delete ${user.username}`}
                        >
                          {busy ? '…' : '🗑'}
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ── Tool Toggles tab ──────────────────────────────────────────────────────────

function ToolTogglesTab() {
  const [toggles,  setToggles]  = useState({})
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const [working,  setWorking]  = useState(null)  // toolPath being updated
  const [toast,    setToast]    = useState(null)

  function showToast(msg, type = 'success') { setToast({ message: msg, type }) }

  async function loadToggles() {
    setLoading(true)
    setError(null)
    try {
      const { data } = await fetchAdminToolToggles()
      if (data?.success) setToggles(data.data.toggles)
      else setError(data?.message || 'Failed to load toggles')
    } catch {
      setError('Network error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadToggles() }, [])

  async function handleToggle(toolPath, enabled) {
    setWorking(toolPath)
    // Optimistic update
    setToggles(prev => ({ ...prev, [toolPath]: enabled }))
    try {
      const { data } = await updateToolToggle(toolPath, enabled)
      if (data?.success) {
        showToast(`${TOOL_META[toolPath]?.label ?? toolPath} ${enabled ? 'enabled' : 'disabled'}`)
      } else {
        // Revert on failure
        setToggles(prev => ({ ...prev, [toolPath]: !enabled }))
        showToast(data?.message || 'Update failed', 'error')
      }
    } catch {
      setToggles(prev => ({ ...prev, [toolPath]: !enabled }))
      showToast('Network error', 'error')
    } finally {
      setWorking(null)
    }
  }

  const sortedPaths = Object.keys(TOOL_META)

  return (
    <div className={styles.tabPanel}>
      {toast && (
        <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />
      )}

      <p className={styles.togglesHint}>
        Disabling a tool hides it from the dashboard for regular users. Admins
        can still navigate to all tools regardless of toggle state.
      </p>

      {loading && <div className={styles.loadingRow}>Loading tool statuses…</div>}
      {error && !loading && <div className={styles.errorBanner}>{error}</div>}

      {!loading && !error && (
        <div className={styles.togglesGrid}>
          {sortedPaths.map(path => {
            const meta    = TOOL_META[path]
            const enabled = toggles[path] !== false // default to true if unknown
            const busy    = working === path
            return (
              <div
                key={path}
                className={`${styles.toolCard} ${!enabled ? styles.toolCardDisabled : ''}`}
              >
                <div className={styles.toolCardLeft}>
                  <span className={styles.toolIcon}>{meta.icon}</span>
                  <div className={styles.toolInfo}>
                    <span className={styles.toolLabel}>{meta.label}</span>
                    <span className={styles.toolPath}>{path}</span>
                  </div>
                </div>
                <div className={styles.toolCardRight}>
                  <span className={`${styles.toolStatus} ${enabled ? styles.statusOn : styles.statusOff}`}>
                    {enabled ? 'Enabled' : 'Disabled'}
                  </span>
                  <ToggleSwitch
                    id={`toggle-${path.replace('/', '')}`}
                    checked={enabled}
                    onChange={val => handleToggle(path, val)}
                    disabled={busy}
                  />
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ── Page shell ────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'users',     label: 'Users',        icon: '👥' },
  { id: 'toggles',   label: 'Tool Toggles', icon: '⚡' },
  { id: 'analytics', label: 'Analytics',    icon: '📊' },   // ← Sprint 18
]

export default function AdminPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('users')

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>

      {/* ── Header ──────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">⚙</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.adminChip}>Admin</span>
          <span className={styles.userBadge}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Admin Panel</div>
          <h1 className={styles.heroTitle}>
            Admin<br />
            <span className={styles.heroAccent}>Panel</span>
          </h1>
          <p className={styles.heroSub}>
            Manage registered users, assign roles, and control which tools
            are available across the application.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>3</span>
            <span className={styles.statLabel}>roles</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>{Object.keys(TOOL_META).length}</span>
            <span className={styles.statLabel}>tools</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>v1</span>
            <span className={styles.statLabel}>RBAC</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>
        <nav className={styles.tabBar} aria-label="Admin sections">
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? styles.tabActive : styles.tab}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className={styles.tabIcon} aria-hidden="true">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </nav>

        <div className={styles.content}>
          {activeTab === 'users'
            ? <UsersTab currentUsername={username} />
            : <ToolTogglesTab />
          }
          { activeTab === 'analytics' && <AnalyticsTab /> }
        </div>
      </main>
    </div>
  )
}
