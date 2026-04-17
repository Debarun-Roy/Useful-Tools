import { useState, useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import {
  generatePassword,
  savePassword,
  fetchAllPasswords,
  exportVaultEntries,
  fetchGeneratedPasswordHistory,
  logoutUser,
} from '../../api/apiClient'
import styles from './PasswordVaultPage.module.css'
import { deleteVaultEntry, updateVaultEntry } from '../../api/apiClient'
import LockedTabContent from '../../components/LockedTabContent/LockedTabContent'

// ─── Constants ────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'generate', label: 'Generate',  icon: '⚡' },
  { id: 'save',     label: 'Save',      icon: '🔒' },
  { id: 'vault',    label: 'My Vault',  icon: '🗄' },
  { id: 'history',  label: 'Generated History', icon: 'â—·' },
]

const CHAR_TYPES = [
  { key: 'Numbers',              label: 'Numbers',           example: '0–9' },
  { key: 'Special Characters',   label: 'Special Chars',     example: '!@#$' },
  { key: 'Uppercase Alphabets',  label: 'Uppercase',         example: 'A–Z' },
  { key: 'Lowercase Alphabets',  label: 'Lowercase',         example: 'a–z' },
]

const MIN_LEN = 8
const MAX_LEN = 128

// ─── Small helpers ────────────────────────────────────────────────────────────

function Pulse() {
  return <span className={styles.pulse} aria-hidden="true" />
}

function StrengthBar({ password }) {
  if (!password) return null

  let score = 0
  if (password.length >= 12) score++
  if (password.length >= 20) score++
  if (/[A-Z]/.test(password)) score++
  if (/[a-z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++

  const pct = Math.round((score / 6) * 100)
  const label = score <= 2 ? 'Weak' : score <= 4 ? 'Fair' : score <= 5 ? 'Strong' : 'Very strong'
  const fillCls = score <= 2 ? styles.barWeak : score <= 4 ? styles.barFair : styles.barStrong
  const labelCls = score <= 2 ? styles.labelWeak : score <= 4 ? styles.labelFair : styles.labelStrong

  return (
    <div className={styles.strengthWrap}>
      <div className={styles.strengthTrack}>
        <div className={`${styles.strengthFill} ${fillCls}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`${styles.strengthLabel} ${labelCls}`}>{label}</span>
    </div>
  )
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* fallback: select the text */
    }
  }

  return (
    <button
      type="button"
      className={copied ? styles.copyBtnDone : styles.copyBtn}
      onClick={handleCopy}
      title="Copy to clipboard"
    >
      {copied ? '✓ Copied' : 'Copy'}
    </button>
  )
}

// ─── Generate tab ─────────────────────────────────────────────────────────────

function GenerateTab({ username }) {
  const [platform,   setPlatform]   = useState('')
  const [length,     setLength]     = useState(16)
  const [mode,       setMode]       = useState('auto')        // 'auto' | 'custom'
  const [counts,     setCounts]     = useState({ Numbers: 4, 'Special Characters': 4, 'Uppercase Alphabets': 4, 'Lowercase Alphabets': 4 })
  const [result,     setResult]     = useState(null)          // { password, length }
  const [loading,    setLoading]    = useState(false)
  const [saving,     setSaving]     = useState(false)
  const [error,      setError]      = useState('')
  const [saveMsg,    setSaveMsg]    = useState('')

  function updateCount(key, val) {
    const n = Math.max(0, Math.min(MAX_LEN, Number(val) || 0))
    setCounts(c => ({ ...c, [key]: n }))
  }

  const customTotal = Object.values(counts).reduce((s, v) => s + v, 0)

  async function handleGenerate(e) {
    e.preventDefault()
    if (!platform.trim()) { setError('Platform name is required.'); return }
    if (mode === 'custom' && customTotal < MIN_LEN) {
      setError(`Custom total is ${customTotal} — must be at least ${MIN_LEN}.`); return
    }
    if (mode === 'custom' && customTotal > MAX_LEN) {
      setError(`Custom total is ${customTotal} — must be at most ${MAX_LEN}.`); return
    }

    setLoading(true); setError(''); setResult(null); setSaveMsg('')

    try {
      const { data } = mode === 'auto'
        ? await generatePassword(username, platform.trim(), length, 'auto')
        : await generatePassword(username, platform.trim(), customTotal, 'custom', counts)

      if (data.success) {
        setResult(data.data)
      } else {
        setError(data.error || 'Generation failed. Please try again.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSaveGenerated() {
    if (!result) return
    setSaving(true); setSaveMsg('')
    try {
      const { data } = await savePassword(username, platform.trim(), result.password)
      if (data.success) {
        setSaveMsg('Saved to vault!')
      } else {
        setSaveMsg(data.error || 'Save failed.')
      }
    } catch {
      setSaveMsg('Could not reach the server.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.panel}>

      <form className={styles.inputCard} onSubmit={handleGenerate}>
        <div>
          <h2 className={styles.cardTitle}>Generate a password</h2>
          <p className={styles.cardHint}>
            Instantly create a cryptographically strong password for any platform.
            Use Auto mode for a randomised character mix, or Custom to control exact counts.
          </p>
        </div>

        {/* Platform */}
        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Platform / website</label>
          <input
            className={styles.textInput}
            type="text"
            value={platform}
            onChange={e => { setPlatform(e.target.value); setError('') }}
            placeholder="e.g. github.com"
            disabled={loading}
          />
        </div>

        {/* Mode selector */}
        <div className={styles.modeToggle}>
          <button
            type="button"
            className={mode === 'auto' ? styles.modeActive : styles.modeBtn}
            onClick={() => setMode('auto')}
            disabled={loading}
          >Auto</button>
          <button
            type="button"
            className={mode === 'custom' ? styles.modeActive : styles.modeBtn}
            onClick={() => setMode('custom')}
            disabled={loading}
          >Custom</button>
        </div>

        {/* Auto: length slider */}
        {mode === 'auto' && (
          <div className={styles.fieldGroup}>
            <div className={styles.sliderHeader}>
              <label className={styles.fieldLabel}>Password length</label>
              <code className={styles.sliderValue}>{length}</code>
            </div>
            <input
              className={styles.slider}
              type="range"
              min={MIN_LEN}
              max={MAX_LEN}
              value={length}
              onChange={e => setLength(Number(e.target.value))}
              disabled={loading}
            />
            <div className={styles.sliderMinMax}>
              <span>{MIN_LEN}</span><span>{MAX_LEN}</span>
            </div>
          </div>
        )}

        {/* Custom: per-type count inputs */}
        {mode === 'custom' && (
          <div className={styles.customGrid}>
            {CHAR_TYPES.map(ct => (
              <div key={ct.key} className={styles.customCell}>
                <span className={styles.customLabel}>{ct.label}</span>
                <span className={styles.customExample}>{ct.example}</span>
                <input
                  className={styles.countInput}
                  type="number"
                  min={0}
                  max={MAX_LEN}
                  value={counts[ct.key]}
                  onChange={e => updateCount(ct.key, e.target.value)}
                  disabled={loading}
                />
              </div>
            ))}
            <div className={styles.customTotal}>
              Total: <strong>{customTotal}</strong> characters
            </div>
          </div>
        )}

        {error && <div className={styles.errorBanner} role="alert">{error}</div>}

        <div className={styles.actionRow}>
          <button className={styles.primaryBtn} type="submit" disabled={loading}>
            {loading ? <><Pulse /><span>Generating…</span></> : '⚡ Generate'}
          </button>
        </div>
      </form>

      {/* Result panel */}
      {result && (
        <div className={styles.resultCard}>
          <div className={styles.resultHeader}>
            <span className={styles.resultPlatform}>{platform}</span>
            <CopyButton text={result.password} />
          </div>
          <code className={styles.passwordDisplay}>{result.password}</code>
          <StrengthBar password={result.password} />
          <div className={styles.resultMeta}>
            {result.length} characters · generated just now
          </div>
          <div className={styles.saveRow}>
            <button
              type="button"
              className={styles.saveBtn}
              onClick={handleSaveGenerated}
              disabled={saving || !!saveMsg}
            >
              {saving ? <><Pulse /><span>Saving…</span></> : '🔒 Save to vault'}
            </button>
            {saveMsg && (
              <span className={saveMsg.includes('!') ? styles.saveSuccess : styles.saveError}>
                {saveMsg}
              </span>
            )}
          </div>
        </div>
      )}

    </div>
  )
}

// ─── Save tab ─────────────────────────────────────────────────────────────────

function SaveTab({ username }) {
  const [platform,  setPlatform]  = useState('')
  const [password,  setPassword]  = useState('')
  const [show,      setShow]      = useState(false)
  const [loading,   setLoading]   = useState(false)
  const [success,   setSuccess]   = useState('')
  const [error,     setError]     = useState('')

  async function handleSave(e) {
    e.preventDefault()
    if (!platform.trim()) { setError('Platform name is required.'); return }
    if (!password)        { setError('Password is required.'); return }

    setLoading(true); setError(''); setSuccess('')

    try {
      const { data } = await savePassword(username, platform.trim(), password)
      if (data.success) {
        setSuccess(`Password for ${platform.trim()} saved successfully.`)
        setPlatform(''); setPassword(''); setShow(false)
      } else {
        setError(data.error || 'Failed to save. Please try again.')
      }
    } catch {
      setError('Could not reach the server. Please check that Tomcat is running.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.panel}>
      <form className={styles.inputCard} onSubmit={handleSave}>
        <div>
          <h2 className={styles.cardTitle}>Save a password</h2>
          <p className={styles.cardHint}>
            Store an existing password in the vault. It is encrypted with RSA-2048
            before being written to the database — only you can decrypt it.
          </p>
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Platform / website</label>
          <input
            className={styles.textInput}
            type="text"
            value={platform}
            onChange={e => { setPlatform(e.target.value); setError(''); setSuccess('') }}
            placeholder="e.g. google.com"
            disabled={loading}
          />
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.fieldLabel}>Password</label>
          <div className={styles.passwordInputWrap}>
            <input
              className={styles.textInput}
              type={show ? 'text' : 'password'}
              value={password}
              onChange={e => { setPassword(e.target.value); setError(''); setSuccess('') }}
              placeholder="Enter the password to store"
              disabled={loading}
            />
            <button
              type="button"
              className={styles.revealToggle}
              onClick={() => setShow(s => !s)}
              tabIndex={-1}
              aria-label={show ? 'Hide password' : 'Show password'}
            >
              {show ? '🙈' : '👁'}
            </button>
          </div>
          {password && <StrengthBar password={password} />}
        </div>

        {error   && <div className={styles.errorBanner}   role="alert">{error}</div>}
        {success && <div className={styles.successBanner} role="status">{success}</div>}

        <div className={styles.actionRow}>
          <button className={styles.primaryBtn} type="submit" disabled={loading}>
            {loading ? <><Pulse /><span>Encrypting…</span></> : '🔒 Encrypt & Save'}
          </button>
        </div>
      </form>
    </div>
  )
}

// ─── Vault tab ────────────────────────────────────────────────────────────────

// function VaultTab() {
//   const [entries,   setEntries]   = useState(null)   // null = not loaded yet
//   const [loading,   setLoading]   = useState(false)
//   const [error,     setError]     = useState('')
//   const [search,    setSearch]    = useState('')
//   const [revealed,  setRevealed]  = useState({})     // { platform: bool }

//   const loadVault = useCallback(async () => {
//     setLoading(true); setError('')
//     try {
//       const { data } = await fetchAllPasswords()
//       if (data.success) {
//         // Convert indexed map to array for easier rendering
//         setEntries(Object.values(data.data))
//       } else {
//         setError(data.error || 'Could not load the vault.')
//       }
//     } catch {
//       setError('Could not reach the server. Please check that Tomcat is running.')
//     } finally {
//       setLoading(false)
//     }
//   }, [])

//   function toggleReveal(platform) {
//     setRevealed(r => ({ ...r, [platform]: !r[platform] }))
//   }

//   const filtered = (entries || []).filter(e =>
//     e.platform.toLowerCase().includes(search.toLowerCase())
//   )

//   return (
//     <div className={styles.panel}>
//       <div className={styles.inputCard}>
//         <div className={styles.vaultHeader}>
//           <div>
//             <h2 className={styles.cardTitle}>My Vault</h2>
//             <p className={styles.cardHint}>
//               All your stored passwords, decrypted on demand.
//             </p>
//           </div>
//           <button
//             type="button"
//             className={styles.refreshBtn}
//             onClick={loadVault}
//             disabled={loading}
//           >
//             {loading ? <><Pulse /><span>Loading…</span></> : entries === null ? '🔓 Open Vault' : '↺ Refresh'}
//           </button>
//         </div>

//         {error && <div className={styles.errorBanner} role="alert">{error}</div>}

//         {entries !== null && (
//           <>
//             {/* Search */}
//             <div className={styles.searchWrap}>
//               <span className={styles.searchIcon}>🔍</span>
//               <input
//                 className={styles.searchInput}
//                 type="text"
//                 placeholder="Search platforms…"
//                 value={search}
//                 onChange={e => setSearch(e.target.value)}
//               />
//             </div>

//             {/* Entry list */}
//             {filtered.length === 0 ? (
//               <p className={styles.emptyVault}>
//                 {entries.length === 0
//                   ? 'No passwords stored yet. Use the Generate or Save tab to add some.'
//                   : `No platforms matching "${search}".`}
//               </p>
//             ) : (
//               <ul className={styles.entryList}>
//                 {filtered.map(entry => {
//                   const isRevealed = !!revealed[entry.platform]
//                   return (
//                     <li key={entry.platform} className={styles.entryRow}>
//                       <div className={styles.entryPlatform}>
//                         <span className={styles.entryIcon} aria-hidden="true">🌐</span>
//                         <span className={styles.entryPlatformName}>{entry.platform}</span>
//                       </div>
//                       <div className={styles.entryPasswordWrap}>
//                         <code className={styles.entryPassword}>
//                           {isRevealed
//                             ? entry.decrypted_password
//                             : '••••••••••••'}
//                         </code>
//                         <button
//                           type="button"
//                           className={styles.revealBtn}
//                           onClick={() => toggleReveal(entry.platform)}
//                         >
//                           {isRevealed ? '🙈 Hide' : '👁 Reveal'}
//                         </button>
//                         {isRevealed && (
//                           <CopyButton text={entry.decrypted_password} />
//                         )}
//                       </div>
//                     </li>
//                   )
//                 })}
//               </ul>
//             )}

//             <div className={styles.vaultFooter}>
//               {filtered.length} of {entries.length} entries
//             </div>
//           </>
//         )}
//       </div>
//     </div>
//   )
// }

function VaultTab() {
  const [entries,   setEntries]   = useState(null)
  const [loading,   setLoading]   = useState(false)
  const [error,     setError]     = useState('')
  const [search,    setSearch]    = useState('')
  const [revealed,  setRevealed]  = useState({})
  // { [platform]: 'confirming-delete' | 'editing' | null }
  const [rowAction, setRowAction] = useState({})
  const [editPass,  setEditPass]  = useState({}) // { [platform]: string }
  const [actionMsg, setActionMsg] = useState({}) // { [platform]: { text, isError } }
  const [exporting, setExporting] = useState(false)
  const [exportMsg, setExportMsg] = useState(null)

  const loadVault = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const { data } = await fetchAllPasswords()
      if (data.success) {
        setEntries(Object.values(data.data))
        setRowAction({}); setRevealed({}); setActionMsg({})
      } else {
        setError(data.error || 'Could not load the vault.')
      }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }, [])

  function toggleReveal(platform) {
    setRevealed(r => ({ ...r, [platform]: !r[platform] }))
  }

  function startDelete(platform) {
    setRowAction(r => ({ ...r, [platform]: 'confirming-delete' }))
    setActionMsg(m => ({ ...m, [platform]: null }))
  }

  function cancelAction(platform) {
    setRowAction(r => ({ ...r, [platform]: null }))
    setEditPass(p => ({ ...p, [platform]: '' }))
  }

  async function confirmDelete(platform) {
    try {
      const { data } = await deleteVaultEntry(platform)
      if (data.success) {
        setEntries(prev => prev.filter(e => e.platform !== platform))
        setRowAction(r => ({ ...r, [platform]: null }))
      } else {
        setActionMsg(m => ({
          ...m, [platform]: { text: data.error || 'Delete failed.', isError: true },
        }))
        setRowAction(r => ({ ...r, [platform]: null }))
      }
    } catch {
      setActionMsg(m => ({
        ...m, [platform]: { text: 'Could not reach the server.', isError: true },
      }))
      setRowAction(r => ({ ...r, [platform]: null }))
    }
  }

  function startEdit(platform) {
    setRowAction(r => ({ ...r, [platform]: 'editing' }))
    setEditPass(p => ({ ...p, [platform]: '' }))
    setActionMsg(m => ({ ...m, [platform]: null }))
  }

  async function confirmEdit(platform) {
    const newPass = editPass[platform] || ''
    if (!newPass.trim()) {
      setActionMsg(m => ({ ...m, [platform]: { text: 'Password cannot be empty.', isError: true } }))
      return
    }
    try {
      const { data } = await updateVaultEntry(platform, newPass)
      if (data.success) {
        setActionMsg(m => ({ ...m, [platform]: { text: '✓ Password updated.', isError: false } }))
        setRowAction(r => ({ ...r, [platform]: null }))
        setEditPass(p => ({ ...p, [platform]: '' }))
      } else {
        setActionMsg(m => ({ ...m, [platform]: { text: data.error || 'Update failed.', isError: true } }))
      }
    } catch {
      setActionMsg(m => ({ ...m, [platform]: { text: 'Could not reach the server.', isError: true } }))
    }
  }

  async function handleExport() {
    setExporting(true)
    setExportMsg(null)

    try {
      const { data } = await exportVaultEntries()
      if (!data.success) {
        setExportMsg({ text: data.error || 'Export failed.', isError: true })
        return
      }

      const fileName = data.data?.fileName || 'usefultools-vault-export.json'
      const payload = data.data?.payload || {}
      const blob = new Blob([JSON.stringify(payload, null, 2)], {
        type: 'application/json',
      })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)

      setExportMsg({
        text: 'Encrypted vault export downloaded successfully.',
        isError: false,
      })
    } catch {
      setExportMsg({ text: 'Could not reach the server.', isError: true })
    } finally {
      setExporting(false)
    }
  }

  const filtered = (entries || []).filter(e =>
    e.platform.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className={styles.panel}>
      <div className={styles.inputCard}>
        <div className={styles.vaultHeader}>
          <div>
            <h2 className={styles.cardTitle}>My Vault</h2>
            <p className={styles.cardHint}>All your stored passwords, decrypted on demand.</p>
          </div>
          <div className={styles.vaultActions}>
            <button
              type="button"
              className={styles.secondaryBtn}
              onClick={handleExport}
              disabled={exporting}
            >
              {exporting ? 'Exporting...' : 'Export Encrypted JSON'}
            </button>
          <button
            type="button"
            className={styles.refreshBtn}
            onClick={loadVault}
            disabled={loading}
          >
            {loading ? <><Pulse /><span>Loading…</span></> : entries === null ? '🔓 Open Vault' : '↺ Refresh'}
          </button>
        </div>
        </div>

        {error && <div className={styles.errorBanner} role="alert">{error}</div>}

        {exportMsg && (
          <div
            className={exportMsg.isError ? styles.errorBanner : styles.successBanner}
            role={exportMsg.isError ? 'alert' : 'status'}
          >
            {exportMsg.text}
          </div>
        )}

        {entries !== null && (
          <>
            <div className={styles.searchWrap}>
              <span className={styles.searchIcon}>🔍</span>
              <input
                className={styles.searchInput}
                type="text"
                placeholder="Search platforms…"
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>

            {filtered.length === 0 ? (
              <p className={styles.emptyVault}>
                {entries.length === 0
                  ? 'No passwords stored yet.'
                  : `No platforms matching "${search}".`}
              </p>
            ) : (
              <ul className={styles.entryList}>
                {filtered.map(entry => {
                  const { platform, decrypted_password } = entry
                  const isRevealed   = !!revealed[platform]
                  const action       = rowAction[platform] || null
                  const msg          = actionMsg[platform] || null

                  return (
                    <li key={platform} className={styles.entryRow}
                        style={{ flexDirection: 'column', alignItems: 'stretch' }}>

                      {/* Main row */}
                      <div style={{ display: 'flex', alignItems: 'center',
                                    justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
                        <div className={styles.entryPlatform}>
                          <span className={styles.entryIcon} aria-hidden="true">🌐</span>
                          <span className={styles.entryPlatformName}>{platform}</span>
                        </div>

                        <div className={styles.entryPasswordWrap}>
                          <code className={styles.entryPassword}>
                            {isRevealed ? decrypted_password : '••••••••••••'}
                          </code>
                          <button type="button" className={styles.revealBtn}
                                  onClick={() => toggleReveal(platform)}>
                            {isRevealed ? '🙈 Hide' : '👁 Reveal'}
                          </button>
                          {isRevealed && <CopyButton text={decrypted_password} />}
                          <button type="button" className={styles.revealBtn}
                                  onClick={() => action === 'editing' ? cancelAction(platform) : startEdit(platform)}>
                            ✏️ {action === 'editing' ? 'Cancel' : 'Edit'}
                          </button>
                          <button type="button"
                                  className={styles.revealBtn}
                                  style={{ color: 'var(--clr-error)', borderColor: 'var(--clr-error-border)' }}
                                  onClick={() => action === 'confirming-delete'
                                    ? cancelAction(platform)
                                    : startDelete(platform)}>
                            🗑 {action === 'confirming-delete' ? 'Cancel' : 'Delete'}
                          </button>
                        </div>
                      </div>

                      {/* Inline edit form */}
                      {action === 'editing' && (
                        <div style={{ display: 'flex', gap: 8, marginTop: 8,
                                      alignItems: 'center', flexWrap: 'wrap' }}>
                          <input
                            type="password"
                            className={styles.textInput}
                            style={{ flex: 1, minWidth: 180 }}
                            placeholder="Enter new password"
                            value={editPass[platform] || ''}
                            onChange={e => setEditPass(p => ({ ...p, [platform]: e.target.value }))}
                            autoFocus
                          />
                          <button type="button" className={styles.saveBtn}
                                  onClick={() => confirmEdit(platform)}>
                            Save
                          </button>
                        </div>
                      )}

                      {/* Inline delete confirmation */}
                      {action === 'confirming-delete' && (
                        <div style={{ display: 'flex', gap: 8, marginTop: 8,
                                      alignItems: 'center', flexWrap: 'wrap' }}>
                          <span style={{ fontSize: 'var(--fs-sm)', color: 'var(--clr-error)' }}>
                            Remove <strong>{platform}</strong> from your vault?
                          </span>
                          <button type="button"
                                  className={styles.saveBtn}
                                  style={{ background: 'var(--clr-error)', color: '#fff',
                                           border: 'none', padding: '7px 14px' }}
                                  onClick={() => confirmDelete(platform)}>
                            Yes, delete
                          </button>
                        </div>
                      )}

                      {/* Action feedback */}
                      {msg && (
                        <div style={{ marginTop: 6, fontSize: 'var(--fs-sm)',
                                      color: msg.isError ? 'var(--clr-error)' : 'var(--clr-success)',
                                      fontWeight: 600 }}>
                          {msg.text}
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}

            <div className={styles.vaultFooter}>
              {filtered.length} of {entries.length} entries
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ─── Main page ────────────────────────────────────────────────────────────────

function GeneratedHistoryTab() {
  const [entries, setEntries] = useState([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [revealed, setRevealed] = useState({})

  const pageSize = 12
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  const loadHistory = useCallback(async (targetPage = 0) => {
    setLoading(true)
    setError('')

    try {
      const { data } = await fetchGeneratedPasswordHistory(targetPage, pageSize)
      if (data.success) {
        setEntries(data.data.entries || [])
        setTotal(data.data.total || 0)
        setPage(targetPage)
      } else {
        setError(data.error || 'Could not load generated-password history.')
      }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadHistory(0)
  }, [loadHistory])

  function toggleReveal(id) {
    setRevealed((current) => ({ ...current, [id]: !current[id] }))
  }

  function formatTimestamp(value) {
    try {
      return new Date(value).toLocaleString()
    } catch {
      return value
    }
  }

  return (
    <div className={styles.panel}>
      <div className={styles.inputCard}>
        <div className={styles.vaultHeader}>
          <div>
            <h2 className={styles.cardTitle}>Generated password history</h2>
            <p className={styles.cardHint}>
              Review the passwords created by the generator, along with their
              character mix and timestamps.
            </p>
          </div>
          <button
            type="button"
            className={styles.refreshBtn}
            onClick={() => loadHistory(page)}
            disabled={loading}
          >
            {loading ? 'Loading...' : 'Refresh'}
          </button>
        </div>

        {error && <div className={styles.errorBanner} role="alert">{error}</div>}

        {!loading && entries.length === 0 && !error && (
          <p className={styles.emptyVault}>
            No generated passwords found yet. Use the Generate tab to create one.
          </p>
        )}

        {entries.length > 0 && (
          <>
            <div className={styles.historyGrid}>
              {entries.map((entry) => {
                const isRevealed = !!revealed[entry.id]
                const totalChars =
                  (entry.numberCount || 0)
                  + (entry.specialCharacterCount || 0)
                  + (entry.lowercaseCount || 0)
                  + (entry.uppercaseCount || 0)

                return (
                  <div key={entry.id} className={styles.historyCard}>
                    <div className={styles.historyCardHeader}>
                      <div>
                        <div className={styles.historyCardTitle}>
                          Generated password #{entry.id}
                        </div>
                        <div className={styles.historyTimestamp}>
                          {formatTimestamp(entry.generatedAt)}
                        </div>
                      </div>
                      <button
                        type="button"
                        className={styles.revealBtn}
                        onClick={() => toggleReveal(entry.id)}
                      >
                        {isRevealed ? 'Hide' : 'Reveal'}
                      </button>
                    </div>

                    <code className={styles.historyPassword}>
                      {isRevealed ? entry.password : '............'}
                    </code>

                    <div className={styles.historyMeta}>
                      <span className={styles.historyBadge}>Length {totalChars}</span>
                      <span className={styles.historyBadge}>Num {entry.numberCount}</span>
                      <span className={styles.historyBadge}>Spec {entry.specialCharacterCount}</span>
                      <span className={styles.historyBadge}>Upper {entry.uppercaseCount}</span>
                      <span className={styles.historyBadge}>Lower {entry.lowercaseCount}</span>
                    </div>

                    {isRevealed && (
                      <div className={styles.historyActions}>
                        <CopyButton text={entry.password} />
                      </div>
                    )}
                  </div>
                )
              })}
            </div>

            <div className={styles.paginationRow}>
              <button
                type="button"
                className={styles.revealBtn}
                onClick={() => loadHistory(page - 1)}
                disabled={page === 0 || loading}
              >
                Previous
              </button>
              <span className={styles.paginationInfo}>
                Page {page + 1} of {totalPages} · {total} total
              </span>
              <button
                type="button"
                className={styles.revealBtn}
                onClick={() => loadHistory(page + 1)}
                disabled={page >= totalPages - 1 || loading}
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default function PasswordVaultPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('generate')
  const isGuest = username === 'Guest User'

  const restrictedTabs = ['save', 'vault', 'history']
  const isTabDisabled = (tabId) => isGuest && restrictedTabs.includes(tabId)

  async function handleLogout() {
    try { await logoutUser() } catch { /* ignore */ }
    logout()
    navigate('/login')
  }

  return (
    <div className={styles.page}>

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.brand}>
            <span className={styles.brandMark} aria-hidden="true">#</span>
            <span className={styles.brandName}>UsefulTools</span>
          </div>
          <button className={styles.backBtn} onClick={() => navigate('/dashboard')}>
            ← Dashboard
          </button>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.userBadge}>{username}</span>
          <button className={styles.logoutBtn} onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      {/* ── Hero ───────────────────────────────────────────────────────── */}
      <section className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBadge}>Sprint 9 · Password Vault</div>
          <h1 className={styles.heroTitle}>
            Password<br />
            <span className={styles.heroTitleAccent}>Vault</span>
          </h1>
          <p className={styles.heroSub}>
            Generate strong passwords, store them encrypted with RSA-2048,
            and retrieve them on demand — all within your private vault.
          </p>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <span className={styles.statValue}>RSA</span>
            <span className={styles.statLabel}>2048-bit encryption</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>128</span>
            <span className={styles.statLabel}>max length</span>
          </div>
          <div className={styles.statCard}>
            <span className={styles.statValue}>4</span>
            <span className={styles.statLabel}>vault sections</span>
          </div>
        </div>
      </section>

      <main className={styles.main}>

        {/* ── Tab bar ──────────────────────────────────────────────────── */}
        <nav className={styles.tabBar} aria-label="Vault sections">
          {TABS.map(tab => {
            const disabled = isTabDisabled(tab.id)
            return (
              <button
                key={tab.id}
                className={
                  disabled
                    ? styles.tabDisabled
                    : activeTab === tab.id
                    ? styles.tabActive
                    : styles.tab
                }
                onClick={() => !disabled && setActiveTab(tab.id)}
                disabled={disabled}
                title={disabled ? 'Please login to access this resource' : undefined}
              >
                <span className={styles.tabIcon} aria-hidden="true">{tab.icon}</span>
                {tab.label}
                {disabled && <span className={styles.lockBadge} aria-hidden="true">🔒</span>}
              </button>
            )
          })}
        </nav>

        {activeTab === 'generate' && <GenerateTab username={username} />}
        {activeTab === 'save'     && <LockedTabContent isLocked={isTabDisabled('save')}><SaveTab username={username} /></LockedTabContent>}
        {activeTab === 'vault'    && <LockedTabContent isLocked={isTabDisabled('vault')}><VaultTab /></LockedTabContent>}
        {activeTab === 'history'  && <LockedTabContent isLocked={isTabDisabled('history')}><GeneratedHistoryTab /></LockedTabContent>}

      </main>
    </div>
  )
}
