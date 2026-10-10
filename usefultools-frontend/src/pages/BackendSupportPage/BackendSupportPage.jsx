import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { fetchBackendCatalog, validateBackendSpec, validateBackendDraft, logoutUser } from '../../api/apiClient'
import AppHeader from '../../components/AppHeader/AppHeader'
import ToolHero from '../../components/ToolHero/ToolHero'
import PageTabs from '../../components/PageTabs/PageTabs'
import { revisionGuard } from './revision'
import SchemaEditor from './SchemaEditor'
import B3Editor from './B3Editor'
import EtlEditor from './EtlEditor'
import RestEditor from './RestEditor'
import styles from './BackendSupportPage.module.css'

export default function BackendSupportPage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [catalog, setCatalog] = useState(null)
  const [catalogError, setCatalogError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedModule = searchParams.get('module')
  const moduleId = ['schema', 'migration', 'view', 'evaluator', 'etl', 'rest'].includes(requestedModule) ? requestedModule : 'schema'
  const [sampleId, setSampleId] = useState('')
  const [mode, setMode] = useState('sample')
  const [draft, setDraft] = useState('')
  const [preview, setPreview] = useState(null)
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState('Choose a sample to preview its specification.')
  const guard = useRef(revisionGuard())
  const pending = useRef(null)
  const navigationRevision = useRef(0)
  const selected = catalog?.modules.find(m => m.id === moduleId)
  const access = catalog?.access
  function invalidate() {
    guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null)
    setStatus('Selection or draft changed. Validate to see current findings.')
  }
  useEffect(() => {
    const controller = new AbortController()
    setCatalogError(''); setCatalog(null)
    fetchBackendCatalog(controller.signal).then(({ data }) => {
      if (controller.signal.aborted) return
      if (!data?.success) throw new Error('catalog')
      setCatalog(data.data)
    }).catch(error => {
      if (error.name !== 'AbortError') setCatalogError('Catalog unavailable. Your draft has been preserved. Retry to check access.')
    })
    return () => controller.abort()
  }, [refresh])
  useEffect(() => () => { guard.current.edit(); pending.current?.abort() }, [])
  useEffect(() => {
    guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null)
    setSampleId(''); setPreview(null)
  }, [moduleId])
  const effectiveSample = sampleId || selected?.sampleIds[0] || ''
  let parsed = null
  try { parsed = JSON.parse(draft) } catch { /* Invalid draft remains editable. */ }
  const targetValue = parsed?.target ? JSON.stringify({ language: parsed.target.language, framework: parsed.target.framework, dialect: parsed.target.dialect }) : ''
  async function validate() {
    pending.current?.abort()
    const ticket = guard.current.start()
    const controller = new AbortController(); pending.current = controller
    let body
    if (mode === 'sample') body = { sampleId: effectiveSample }
    else {
      try { body = { request: JSON.parse(draft) } }
      catch { setResult(null); setStatus('Invalid JSON. Correct the editor before validation.'); return }
      if (new TextEncoder().encode('{"request":' + draft + '}').length > catalog.limits.bodyBytes) {
        setResult(null); setStatus('Specification exceeds the 1 MiB request limit.'); return
      }
    }
    setBusy(true); setResult(null); setStatus('Validating specification…')
    try {
      const { data } = await (mode === 'custom' ? validateBackendDraft(draft, controller.signal) : validateBackendSpec(body, controller.signal))
      if (!guard.current.current(ticket)) return
      if (data?.data?.result) {
        setResult(data.data)
        if (data.data.sampleRequest) setPreview(data.data.sampleRequest)
        setStatus(data.data.result.valid ? 'Specification valid for B1 checks. No code was generated.' : 'Specification invalid. Review the findings below.')
      } else {
        const code = data?.errorCode || 'REQUEST_FAILED'
        setStatus(`Validation rejected (${code}). Your draft has been preserved. Retry or refresh access.`)
      }
    } catch (error) {
      if (guard.current.current(ticket) && error.name !== 'AbortError') setStatus('Network error. Your draft has been preserved. Retry validation.')
    } finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  async function signOut() { try { await logoutUser() } finally { logout(); navigate('/login') } }
  function changeMode(next) {
    invalidate(); setMode(next)
    if (next === 'custom' && !draft && preview) setDraft(JSON.stringify(preview, null, 2))
  }
  return <div className={styles.page}>
    <AppHeader username={username} onLogout={signOut} onBack={() => navigate('/dashboard')} glyph="⌘" />
    <main className={styles.main}>
      <ToolHero badge="Backend tools" title="Backend Support" description="Configure database tools, local Python ETL jobs, and standalone Java and Python auth starters." />
      <section className={styles.panel} aria-label="Backend specification workspace">
        {!catalog && !catalogError && <p role="status">Loading capability catalog…</p>}
        {catalogError && <p role="alert">{catalogError}</p>}
        <button type="button" onClick={() => { invalidate(); setRefresh(r => r + 1) }}>Refresh access</button>
        {catalog && <>
          <p className={styles.notice}>{!access.configured ? 'Tool configuration is missing. Validation is unavailable.' : access.adminPreview ? 'Admin preview: this tool is disabled for other users.' : !access.enabled ? 'This tool is disabled. An administrator must enable it before validation.' : access.sampleOnly ? 'Guest access: immutable built-in samples only.' : 'Custom specifications and built-in sample previews are available.'}</p>
          <p className={styles.meta}>Catalog {catalog.catalogVersion} · Schema {catalog.schemaVersions.join(', ')} · Template {catalog.templateVersion} (draft, not a generator)</p>
          <div className={styles.controls}>
            <label>Tool family<select value={moduleId} onChange={e => { navigationRevision.current++; invalidate(); setSearchParams({ module: e.target.value }); setSampleId(''); setPreview(null) }}>
              {catalog.modules.map(m => <option key={m.id} value={m.id}>{m.label}</option>)}
            </select></label>
            <label>Built-in sample<select value={effectiveSample} onChange={e => { invalidate(); setSampleId(e.target.value); setPreview(null) }}>
              {selected?.sampleIds.map(id => <option key={id} value={id}>{id}</option>)}
            </select></label>
          </div>
          <SchemaEditor capability={catalog.generation} active={moduleId === 'schema'} />
          {['migration', 'view', 'evaluator'].map(module => <B3Editor key={module} module={module} capability={catalog.generationModules?.find(c => c.module === module)} active={moduleId === module} />)}
          <EtlEditor capability={catalog.generationModules?.find(c => c.module === 'etl')} active={moduleId === 'etl'} />
          <RestEditor navigationRevision={navigationRevision} capability={catalog.generationModules?.find(c => c.module === 'rest')} active={moduleId === 'rest'} />
          <h2>Historical draft validation (0.1.0)</h2>
          <p>Applicable targets: {selected?.targets.map(t => `${t.language} / ${t.framework} / ${t.dialect}`).join(' · ')}</p>
          <PageTabs ariaLabel="Specification input" activeTab={mode} onChange={changeMode} tabs={[{ id: 'sample', label: 'Sample preview' }, ...(access.canCustom ? [{ id: 'custom', label: 'Custom JSON' }] : [])]} />
          {mode === 'sample' ? <>
            <p>Load and validate the selected server-owned sample. Its specification is read-only.</p>
            <label htmlFor="b1-sample">Sample specification</label><textarea id="b1-sample" readOnly value={preview ? JSON.stringify(preview, null, 2) : ''} placeholder="The selected sample will appear here after validation." rows={14} />
          </> : <>
            <p id="draft-help">Edit a complete GenerationRequest. Maximum 1 MiB; strings 4,096 characters; 100 tables and 1,000 columns per schema. Drafts are not saved in browser storage.</p>
            <label htmlFor="b1-draft">Custom specification JSON</label><textarea id="b1-draft" aria-describedby="draft-help" value={draft} maxLength={1048576} rows={18} spellCheck={false} onChange={e => { invalidate(); setDraft(e.target.value) }} />
            <label>Custom target<select value={targetValue} disabled={!parsed || typeof parsed !== 'object' || Array.isArray(parsed)} onChange={e => {
              invalidate(); const target = JSON.parse(e.target.value)
              setDraft(JSON.stringify({ ...parsed, target }, null, 2))
            }}><option value="" disabled>Choose a target after entering JSON</option>
              {selected?.targets.map(t => <option key={JSON.stringify(t)} value={JSON.stringify(t)}>{t.language} / {t.framework} / {t.dialect}</option>)}
            </select></label>
          </>}
          <div className={styles.actions}><button type="button" disabled={busy || !access.canValidate || (mode === 'custom' && !access.canCustom)} onClick={validate}>{busy ? 'Validating…' : mode === 'sample' ? 'Load and validate sample' : 'Validate custom specification'}</button></div>
        </>}
        <p role="status" aria-label="Validation status" aria-live="polite" aria-atomic="true">{status}</p>
        {result && <section aria-label="Validation findings">
          <h2>Validation findings</h2>
          {result.truncated && <p>Findings truncated at 100. Correct these and validate again.</p>}
          {result.result.findings.length === 0 ? <p>No findings from B1 checks.</p> : <ol>{result.result.findings.map((f, i) => <li key={`${f.ruleId}-${f.path}-${i}`}><strong>{f.severity}: {f.ruleId}</strong><br /><code>{f.path}</code><p>{f.message} {f.suggestion}</p></li>)}</ol>}
        </section>}
      </section>
    </main>
  </div>
}
