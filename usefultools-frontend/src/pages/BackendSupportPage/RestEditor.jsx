import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { generateBackendSchema, exportBackendSchema } from '../../api/apiClient'
import { revisionGuard } from './revision'
import javaSample from './restSample.json'
import pythonSample from './pythonRestSample.json'
import styles from './BackendSupportPage.module.css'

const text = value => JSON.stringify(value, null, 2)
function editable(s, sample) {
  return s?.module === 'rest' && ['project', sample.target === 'python' ? 'pythonModule' : 'packageName', 'database', 'mode', 'target'].every(k => typeof s[k] === 'string') && Array.isArray(s.modules) && s.modules.every(v => typeof v === 'string') && Array.isArray(s.origins) && s.origins.every(v => typeof v === 'string') && ['session', 'policy', 'profile', 'captcha', 'runtime'].every(k => s[k] && typeof s[k] === 'object' && !Array.isArray(s[k])) && Object.keys(sample.session).every(k => typeof s.session[k] === 'number') && Object.keys(sample.runtime).every(k => typeof s.runtime[k] === 'string') && ['mode','hostname','secretEnv'].every(k => typeof s.captcha[k] === 'string')
}
function Select({ label, value, values, onChange }) {
  return <label>{label}<select aria-label={label} value={value} onChange={e => onChange(e.target.value)}>{values.map(v => <option key={v} value={v}>{v === '\t' ? 'tab' : v}</option>)}</select></label>
}
export default function RestEditor({ capability, active, navigationRevision }) {
  const [target, setTarget] = useState('java')
  function changeTarget(value) { navigationRevision.current++; setTarget(value) }
  return <>
    <AuthEditor sample={javaSample} capability={capability} active={active && target === 'java'} onTargetChange={changeTarget} navigationRevision={navigationRevision} />
    <AuthEditor sample={pythonSample} capability={{ ...capability, ...capability?.python }} active={active && target === 'python'} onTargetChange={changeTarget} navigationRevision={navigationRevision} />
  </>
}
function AuthEditor({ sample, capability, active, onTargetChange, navigationRevision }) {
  const python = sample.target === 'python'
  const title = python ? 'Python auth starter' : 'Java auth starter'
  const [draft, setDraft] = useState(() => structuredClone(sample)), [json, setJson] = useState(() => text(sample))
  const [unapplied, setUnapplied] = useState(false), [busy, setBusy] = useState(false), [result, setResult] = useState(null)
  const [findings, setFindings] = useState([]), [file, setFile] = useState(''), [status, setStatus] = useState('Configure a Java auth starter. Drafts stay in memory only.')
  const guard = useRef(revisionGuard()), pending = useRef(null), editor = useRef(null)
  function invalidate(message = 'Draft changed. Generate a fresh preview before export.') { guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null); setFindings([]); setStatus(message) }
  useEffect(() => () => { guard.current.edit(); pending.current?.abort() }, [])
  useLayoutEffect(() => { invalidate('Module or access changed. Generate again to check current access.') }, [active, capability?.canGenerate, capability?.canExport, navigationRevision.current])
  function edit(update) { invalidate(); const next = structuredClone(draft); update(next); setDraft(next); setJson(text(next)); setUnapplied(false) }
  function apply() {
    try { const next = JSON.parse(json); if (!editable(next, sample) || next.target !== sample.target) throw new Error('shape'); setDraft(next); setUnapplied(false); invalidate('JSON applied. The server validates all semantics before export.') }
    catch { setStatus('Invalid JSON or missing guided fields. Input preserved; correct it or explicitly discard changes.') }
  }
  async function generate() {
    if (new TextEncoder().encode('{"request":' + json + '}').length > 1048576) { setStatus('Request exceeds 1 MiB. Reduce the specification.'); return }
    pending.current?.abort(); const controller = new AbortController(); pending.current = controller; const ticket = guard.current.start(), navigation = navigationRevision.current
    setBusy(true); setResult(null); setFindings([]); setStatus('Validating auth specification…')
    try {
      const { data } = await generateBackendSchema(json, controller.signal)
      if (!guard.current.current(ticket) || navigation !== navigationRevision.current) return
      setFindings(data?.data?.findings || [])
      if (data?.success) { setResult({ ...data.data, requestText: json }); setFile('README.md'); setStatus('auth bundle ready for review. Generation does not execute the app or connect to a database.') }
      else setStatus(`Generation blocked (${data?.errorCode || 'REQUEST_FAILED'}). Input preserved.`)
    } catch (e) { if (guard.current.current(ticket) && navigation === navigationRevision.current && e.name !== 'AbortError') setStatus('Network failure. Input preserved; retry.') }
    finally { if (guard.current.current(ticket) && navigation === navigationRevision.current) setBusy(false) }
  }
  async function download() {
    if (!result) return
    const snapshot = result, ticket = guard.current.start(), navigation = navigationRevision.current, controller = new AbortController(); pending.current?.abort(); pending.current = controller; setBusy(true)
    try {
      const response = await exportBackendSchema(snapshot.requestText, snapshot.digest, controller.signal)
      if (!guard.current.current(ticket) || navigation !== navigationRevision.current) return
      if (response.blob) {
        const url = URL.createObjectURL(response.blob), a = document.createElement('a'); a.href = url; a.download = 'usefultools-rest.zip'; document.body.appendChild(a)
        try { a.click() } finally { a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
        setStatus('auth bundle downloaded after revalidation. Follow its README in an isolated local environment.')
      } else setStatus(`Export failed (${response.data?.errorCode || 'TRANSPORT_ERROR'}). Input preserved.`)
    } catch (e) { if (guard.current.current(ticket) && navigation === navigationRevision.current && e.name !== 'AbortError') setStatus('Export network failure. Input preserved.') }
    finally { if (guard.current.current(ticket) && navigation === navigationRevision.current) setBusy(false) }
  }
  const activeFile = result?.files.find(f => f.path === file)
  async function copyArtifact() {
    if (!activeFile) return
    try { await navigator.clipboard.writeText(activeFile.content); setStatus('Artifact copied.') }
    catch { setStatus('Clipboard unavailable. Select the preview text to copy; input preserved.') }
  }
  return <section aria-label={title} hidden={!active}>
    <h2>{title}</h2>
    <p>Standalone {python ? 'FastAPI' : 'Servlet'} application with registration, login, logout, sessions and CSRF. Contract {capability?.schemaVersion} · template {capability?.templateVersion}. Enter environment variable names, never real credentials.</p>
    {!capability?.canGenerate && <p>Generation requires registered access and an enabled tool, or disabled-tool admin preview.</p>}
    <label>Auth target<select aria-label="Auth target" value={draft.target} onChange={e => onTargetChange(e.target.value)}><option value="java">Java · Jakarta Servlets</option><option value="python">Python · FastAPI</option></select></label>
    <p>Java and Python drafts stay independent, including unapplied JSON. Switching targets never converts or discards either draft.</p>
    <fieldset disabled={unapplied}><legend>Auth guided configuration</legend>
      <div className={styles.controls}>

        <Select label="Auth database" value={draft.database} values={['sqlite', 'postgresql']} onChange={v => edit(n => { n.database = v })} />
        <label>Project identifier<input maxLength={40} value={draft.project} onChange={e => edit(n => { n.project = e.target.value })} /></label>
        {python ? <label>Python module<input maxLength={40} value={draft.pythonModule} onChange={e => edit(n => { n.pythonModule = e.target.value })} /></label> : <label>Java package<input maxLength={100} value={draft.packageName} onChange={e => edit(n => { n.packageName = e.target.value })} /></label>}
        <Select label="Deployment mode" value={draft.mode} values={['production', 'development']} onChange={v => edit(n => { n.mode = v })} />
      </div>
      <p>{python ? 'Python 3.14 / FastAPI. Requires real Redis and one worker; each restart revokes sessions.' : 'Tomcat 11 / Servlet 6.1.'} Production requires HTTPS. Explicit development permits HTTP only on loopback; changing mode never silently changes trusted origins.</p>
      <fieldset><legend>Endpoint modules</legend>
        <label><span><input type="checkbox" checked readOnly /> Core authentication · required</span></label>
        <label><span><input type="checkbox" checked={draft.modules.includes('profile')} onChange={e => edit(n => { n.modules = e.target.checked ? ['core', 'profile'] : ['core'] })} /> Profile GET and PATCH</span></label>
        <p>Core includes registration, login, logout, session and CSRF bootstrap. It requires user persistence, Argon2id password verification, session authentication, Origin/CSRF protection and throttling. Profile requires core. Unsupported dependencies are rejected before export.</p>
      </fieldset>
      <label>Trusted application origins · one per line<textarea rows={3} value={draft.origins.join('\n')} onChange={e => edit(n => { n.origins = e.target.value.split('\n') })} /></label>
      <div className={styles.controls}>
        {Object.keys(sample.session).map(key => <label key={key}>Session {key}<input type="number" value={draft.session[key]} onChange={e => edit(n => { n.session[key] = Number(e.target.value) })} /></label>)}
        {['passwordMin', 'passwordMax'].map(key => <label key={key}>{key}<input type="number" min={15} max={128} value={draft.policy[key]} onChange={e => edit(n => { n.policy[key] = Number(e.target.value) })} /></label>)}
      </div>
      <p>Usernames are 3–32 ASCII letters, digits or underscores, normalized lowercase. Passwords remain unchanged and are bounded to 512 UTF-8 bytes. Registration returns 201 without login; login rotates session and CSRF.</p>
      {draft.modules.includes('profile') && <fieldset><legend>Profile limits</legend><div className={styles.controls}>{Object.keys(sample.profile).map(key => <label key={key}>{key}<input type="number" value={draft.profile[key]} onChange={e => edit(n => { n.profile[key] = Number(e.target.value) })} /></label>)}</div><p>Only display name and bounded string preferences can change. IDs, usernames, roles and owner fields cannot be assigned.</p></fieldset>}
      <fieldset><legend>CAPTCHA policy</legend><div className={styles.controls}>
        <Select label="CAPTCHA mode" value={draft.captcha.mode} values={['off', 'recaptcha-v3']} onChange={v => edit(n => { n.captcha.mode = v })} />
        <label>CAPTCHA secret environment name<input maxLength={64} value={draft.captcha.secretEnv} onChange={e => edit(n => { n.captcha.secretEnv = e.target.value })} /></label>
        <label>Expected CAPTCHA hostname<input maxLength={253} value={draft.captcha.hostname} onChange={e => edit(n => { n.captcha.hostname = e.target.value })} /></label>
        <label>Minimum CAPTCHA score<input type="number" min={0.1} max={1} step={0.1} value={draft.captcha.minimumScore} onChange={e => edit(n => { n.captcha.minimumScore = Number(e.target.value) })} /></label>
      </div><p>{draft.captcha.mode === 'off' ? 'CAPTCHA is explicitly disabled.' : 'Login and registration require reCAPTCHA v3. Missing secrets fail startup; provider failures fail closed. The public site key belongs in your own frontend.'}</p></fieldset>
      <fieldset><legend>Auth runtime environment variable names</legend><div className={styles.controls}>{Object.keys(sample.runtime).map(key => <label key={key}>Auth {key}<input maxLength={64} value={draft.runtime[key]} onChange={e => edit(n => { n.runtime[key] = e.target.value })} /></label>)}</div></fieldset>
    </fieldset>
    <label>Auth specification JSON<textarea ref={editor} rows={16} spellCheck={false} value={json} onChange={e => { setJson(e.target.value); setUnapplied(true); invalidate('Unapplied JSON preserved. Apply or discard before guided editing.') }} /></label>
    <div className={styles.actions}>
      <button type="button" disabled={!unapplied} onClick={apply}>Apply auth JSON</button>
      <button type="button" disabled={!unapplied} onClick={() => { setJson(text(draft)); setUnapplied(false); invalidate('Unapplied changes discarded explicitly.') }}>Discard auth JSON changes</button>
      <button type="button" disabled={busy || !capability?.canGenerate} onClick={generate}>Generate auth preview</button>
      <button type="button" disabled={busy || !result || !capability?.canExport} onClick={download}>Download auth ZIP</button>
    </div>
    <p role="status" aria-live="polite">{status}</p>
    {findings.length > 0 && <ul>{findings.map((f, i) => <li key={i}><button type="button" onClick={() => { editor.current?.focus(); setStatus(`Correct ${f.path} in the preserved JSON.`) }}>{f.ruleId} · {f.path}</button></li>)}</ul>}
    {result && <><p>Bundle digest: <code>{result.digest}</code></p><label>Auth preview file<select value={file} onChange={e => setFile(e.target.value)}>{result.files.map(f => <option key={f.path}>{f.path}</option>)}</select></label><button type="button" onClick={copyArtifact}>Copy auth artifact</button><pre aria-label="Auth artifact content">{activeFile?.content}</pre></>}
  </section>
}
