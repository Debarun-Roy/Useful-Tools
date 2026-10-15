import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { fetchEmoteCategories, fetchEmoteCatalog, logoutUser, logActivity } from '../../api/apiClient'
import useEmoteAccess from '../../hooks/useEmoteAccess'
import AppHeader from '../../components/AppHeader/AppHeader'
import ThemePicker from '../../components/ThemePicker/ThemePicker'
import { TYPES, parseQuery, queryString, catalogParams } from './query'
import styles from './EmoteUtilsPage.module.css'

export default function EmoteUtilsPage() {
  const { username, logout, isGuest } = useAuth()
  const access = useEmoteAccess(), navigate = useNavigate()
  async function signOut() { logout(); try { await logoutUser() } catch { /* local identity is already cleared */ } }
  return <div className={styles.page}>
    <AppHeader username={username} onLogout={signOut} onBack={() => navigate('/dashboard')} glyph=":)" />
    <main className={styles.main}>
      <div className={styles.title}><div><p>Expressions, ready to copy</p><h1>EmoteUtils</h1></div><ThemePicker /></div>
      <p>Browse emoji, text faces, kaomoji and symbols. Appearance depends on your platform; names and code points remain available.</p>
      {access.status === 'enabled' ? <Catalog key={access.identity} isGuest={isGuest} /> : <section className={styles.state} aria-live="polite">
        <h2>{access.status === 'checking' ? 'Checking availability…' : access.status === 'disabled' ? 'EmoteUtils is disabled' : access.status === 'offline' ? 'You’re offline' : access.status === 'expired' ? 'Please sign in again' : 'EmoteUtils is unavailable'}</h2>
        <p>{access.status === 'disabled' ? 'An administrator has turned this tool off.' : 'Catalog access requires an active connection and session.'}</p>
        {access.status !== 'checking' && <button onClick={access.refresh}>Check again</button>}
      </section>}
    </main>
  </div>
}

function Catalog({ isGuest }) {
  const [params, setParams] = useSearchParams()
  const query = parseQuery(params), canonical = queryString(query)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ key: '', status: 'loading' })
  const [notice, setNotice] = useState('')
  const [dialog, setDialog] = useState(null)
  const [feedback, setFeedback] = useState('')
  const life = useRef(null), copySequence = useRef(0), heading = useRef(null), pageFocus = useRef(false)
  const version = useRef(null), visited = useRef(false)
  const key = `${canonical}|${attempt}`
  const active = state.key === key ? state : { status: 'loading' }
  useEffect(() => {
    if (params.toString() !== canonical) setParams(canonical, { replace: true })
  }, [params, canonical, setParams])
  useEffect(() => {
    const token = {}; life.current = token
    const controller = new AbortController()
    const current = () => life.current === token && !controller.signal.aborted
    const q = parseQuery(canonical)
    const run = async () => {
      if (!current()) return
      setDialog(null); setFeedback(''); copySequence.current++
      setState({ key, status: 'loading' })
      try {
        const metadata = await fetchEmoteCategories({ type: q.type, includeAlternates: String(q.includeAlternates) }, controller.signal)
        if (!current()) return
        if (!metadata.data?.success) throw metadata
        const meta = metadata.data.data
        const category = meta.categories.find(c => c.id === q.category)
        if (q.category && !category || q.subgroup && !category?.subgroups.some(s => s.id === q.subgroup)) {
          setNotice('The filters were reset because that category is no longer available.')
          setParams(queryString({ ...q, category: '', subgroup: '', offset: 0 }), { replace: true }); return
        }
        if (q.type === 'all' && category) { setParams(queryString({ ...q, type: category.type, offset: 0 }), { replace: true }); return }
        if (version.current && version.current !== meta.catalogVersion) {
          setNotice('The catalog changed. Showing the first page.')
        }
        if (version.current && version.current !== meta.catalogVersion && q.offset) {
          version.current = meta.catalogVersion; setNotice('The catalog changed. Showing the first page.')
          setParams(queryString({ ...q, offset: 0 }), { replace: true }); return
        }
        version.current = meta.catalogVersion
        let response = await fetchEmoteCatalog(catalogParams(q, meta.catalogVersion), controller.signal)
        if (!current()) return
        // Exactly one recovery attempt per user request; repeated switches surface Retry.
        if (response.status === 409) {
          setNotice('The catalog changed. Showing the first page.')
          if (q.offset) { setParams(queryString({ ...q, offset: 0 }), { replace: true }); return }
          const fresh = await fetchEmoteCategories({ type: q.type, includeAlternates: String(q.includeAlternates) }, controller.signal)
          if (!current()) return
          if (!fresh.data?.success) throw fresh
          Object.assign(meta, fresh.data.data); version.current = meta.catalogVersion
          response = await fetchEmoteCatalog(catalogParams(q, meta.catalogVersion), controller.signal)
          if (!current()) return
        }
        if (!response.data?.success) throw response
        const data = response.data.data
        if (data.catalogVersion !== meta.catalogVersion || data.items.length > 100) throw { status: 503 }
        if (q.offset && q.offset >= data.total) {
          setParams(queryString({ ...q, offset: Math.max(0, Math.floor((data.total - 1) / 60) * 60) }), { replace: true }); return
        }
        setState({ key, status: 'ready', meta, data })
        if (!visited.current && !isGuest) { visited.current = true; logActivity('Opened EmoteUtils', 'emote-utils.visit', {}).catch(() => {}) }
        if (pageFocus.current) { pageFocus.current = false; requestAnimationFrame(() => { if (current()) heading.current?.focus() }) }
      } catch (error) {
        if (!current()) return
        setState({ key, status: 'error', code: error.status || (navigator.onLine ? 'network' : 'offline') })
        if (error.status === 403) window.dispatchEvent(new Event('emote-availability'))
      }
    }
    queueMicrotask(run)
    return () => { controller.abort(); if (life.current === token) life.current = null }
  }, [canonical, key, isGuest, setParams])
  function update(change, paging = false) {
    pageFocus.current = paging
    setParams(queryString({ ...query, offset: 0, ...change }))
  }
  function open(entry, origin, manual = false) {
    if (!manual) { copySequence.current++; setFeedback('') }
    setDialog(previous => ({ entry, origin: previous?.key === key && previous.entry.id === entry.id ? previous.origin : origin, manual, key }))
  }
  async function copy(entry, origin) {
    const seq = ++copySequence.current, token = life.current
    setFeedback(`Copying ${entry.name}…`)
    try {
      if (!window.isSecureContext || !navigator.clipboard?.writeText) throw new Error('Unavailable')
      await navigator.clipboard.writeText(entry.value)
      if (life.current === token && seq === copySequence.current) setFeedback(`Copied ${entry.name}`)
    } catch {
      if (life.current === token && seq === copySequence.current) {
        setFeedback('Automatic copy was not available. Use the exact text in the dialog.'); open(entry, origin, true)
      }
    }
  }
  const categories = active.meta?.categories || []
  const subgroup = categories.find(c => c.id === query.category)?.subgroups || []
  const errorText = { 400: 'These filters could not be used. Reset them to continue.', 401: 'Your session expired. Please sign in again.', 403: 'EmoteUtils is disabled.', 409: 'The catalog keeps changing. Please retry.', 503: 'The catalog is temporarily unavailable. Please retry.', network: 'The connection failed. Your filters are preserved.', offline: 'You’re offline. Reconnect and retry to load the catalog.' }
  return <>
    <div className={styles.controls}>
      <label>Search names or expressions<input type="search" value={query.q} onChange={e => update({ q: [...e.target.value].slice(0, 100).join('') })} /></label>
      <label>Expression type<select aria-label="Expression type" value={query.type} onChange={e => update({ type: e.target.value, category: '', subgroup: '' })}>{Object.entries(TYPES).map(([v, text]) => <option key={v} value={v}>{text}</option>)}</select></label>
      <label className={styles.check}><input type="checkbox" checked={query.includeAlternates} onChange={e => update({ includeAlternates: e.target.checked })} />Include alternate emoji forms</label>
    </div>
    <p>Alternate forms preserve text and emoji presentation variants; their appearance may differ across platforms.</p>
    <p className={styles.notice} role="status">{notice}</p>
    <div className={styles.layout}>
      <aside aria-label="Categories" className={styles.sidebar}><h2>Categories</h2><button aria-pressed={!query.category} onClick={() => update({ category: '', subgroup: '' })}>All categories</button>{categories.map(c => <button key={`${c.type}:${c.id}`} aria-pressed={query.category === c.id && (query.type === c.type || query.type === 'all')} onClick={() => update({ type: c.type, category: c.id, subgroup: '' })}>{c.label} ({c.count})</button>)}</aside>
      <section className={styles.results} aria-label="Catalog results" data-query={active.status === 'ready' ? canonical : undefined} aria-busy={active.status === 'loading'}>
        <label className={styles.mobile}>Category<select aria-label="Category" value={query.category ? JSON.stringify([query.type, query.category]) : ""} onChange={e => { const [type, category] = e.target.value ? JSON.parse(e.target.value) : [query.type, ""]; update({ type, category, subgroup: "" }) }}><option value="">All categories</option>{categories.map(c => <option key={`${c.type}:${c.id}`} value={JSON.stringify([c.type, c.id])}>{c.label} ({c.count})</option>)}</select></label>
        {query.type === 'emoji' && query.category && <label>Subgroup<select aria-label="Subgroup" value={query.subgroup} onChange={e => update({ subgroup: e.target.value })}><option value="">All subgroups</option>{subgroup.map(s => <option key={s.id} value={s.id}>{s.label} ({s.count})</option>)}</select></label>}
        <h2 ref={heading} tabIndex={-1} className={styles.resultTitle}>Expressions</h2>
        <p role="status">{active.status === 'ready' ? `${active.data.total} results · ${active.data.total ? query.offset + 1 : 0}–${Math.min(query.offset + 60, active.data.total)}` : active.status === 'loading' ? 'Loading expressions…' : ''}</p>
        {active.status === 'error' && <div role="alert" className={styles.state}><p>{errorText[active.code] || errorText.network}</p><button onClick={() => setAttempt(n => n + 1)}>Retry</button><button onClick={() => { setParams(queryString(parseQuery(''))); setAttempt(n => n + 1) }}>Reset filters</button></div>}
        {active.status === 'loading' && <div className={styles.loading} aria-hidden="true">Loading…</div>}
        {active.status === 'ready' && <>
          {!active.data.items.length && <div className={styles.state}><p>No expressions match these filters.</p><button onClick={() => update({ q: '', category: '', subgroup: '' })}>Clear filters</button></div>}
          <ul className={styles.grid}>{active.data.items.map(entry => <li className={styles.card} key={`${active.data.catalogVersion}:${entry.id}`} data-emote-id={entry.id}>
            <div className={`${styles.preview} ${entry.kind !== 'emoji' ? styles.textPreview : ''}`} aria-hidden="true">{entry.value}</div>
            <h3 title={entry.name}>{entry.name}</h3>
            <div className={styles.actions}><button aria-label={`Copy ${entry.name}`} onClick={e => copy(entry, e.currentTarget)}>Copy</button><button aria-label={`Expand ${entry.name}`} onClick={e => open(entry, e.currentTarget)}>Expand</button></div>
          </li>)}</ul>
          <nav className={styles.paging} aria-label="Result pages"><button disabled={!query.offset} onClick={() => update({ offset: Math.max(0, query.offset - 60) }, true)}>Previous</button><span>Page {Math.floor(query.offset / 60) + 1}</span><button disabled={!active.data.hasMore} onClick={() => update({ offset: query.offset + 60 }, true)}>Next</button></nav>
        </>}
      </section>
    </div>
    <p role="status" className={styles.feedback}>{active.status === 'ready' && !dialog ? feedback : ''}</p>
    {dialog?.key === key && active.status === 'ready' && <ValueDialog entry={dialog.entry} manual={dialog.manual} feedback={feedback} onCopy={e => copy(dialog.entry, e.currentTarget)} onClose={() => { copySequence.current++; setFeedback(''); setDialog(null); requestAnimationFrame(() => (dialog.origin?.isConnected ? dialog.origin : heading.current)?.focus()) }} />}
  </>
}

export function ValueDialog({ entry, manual, feedback, onCopy, onClose }) {
  const ref = useRef(null), close = useRef(null)
  useEffect(() => { const dialog = ref.current; dialog.showModal(); close.current.focus(); return () => dialog.close() }, [])
  function containFocus(event) {
    if (event.key !== 'Tab') return
    const controls = [...ref.current.querySelectorAll('button:not(:disabled), textarea, [tabindex="0"]')]
    const first = controls[0], last = controls[controls.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
  return <dialog onKeyDown={containFocus} ref={ref} className={styles.dialog} aria-labelledby="emote-value-name" onCancel={e => { e.preventDefault(); onClose() }}>
    <h2 id="emote-value-name">{entry.name}</h2>
    <p>{manual && !feedback?.startsWith('Copied ') ? 'Copy was not completed. Select the exact text, then use your device’s Copy command.' : 'Full exact expression. Select the text or use Copy.'}</p>
    <p role="status">{feedback}</p>
    <label>Exact expression<textarea readOnly value={entry.value} spellCheck={false} wrap="off" onFocus={e => e.currentTarget.select()} /></label>
    <p className={styles.codepoints}>{[...entry.value].map(c => `U+${c.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`).join(' ')}</p>
    <div className={styles.actions}><button onClick={onCopy} aria-label={`Copy ${entry.name}`}>Copy</button><button ref={close} onClick={onClose}>Close expression</button></div>
  </dialog>
}
