import { useEffect, useRef, useState } from 'react'
import { generateBackendSchema, exportBackendSchema } from '../../api/apiClient'
import { revisionGuard } from './revision'
import sample from './schemaSample.json'
import styles from './BackendSupportPage.module.css'

const types = ['integer', 'bigint', 'decimal', 'text', 'varchar', 'boolean', 'date', 'timestamp', 'json']
const clone = value => structuredClone(value)
const text = value => JSON.stringify(value, null, 2)
function save(blob, name) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = name
  document.body.appendChild(link)
  try { link.click() } finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
// A shape guard protects forms only. The server independently performs strict contract validation.
function editable(value) {
  return value && typeof value === 'object' && ['schemaVersion', 'templateVersion', 'module', 'target'].every(k => typeof value[k] === 'string') && Array.isArray(value.tables) && value.tables.length > 0 &&
    value.tables.every(t => t && typeof t.id === 'string' && typeof t.name === 'string' && Array.isArray(t.columns) &&
      t.columns.every(c => c && typeof c.nullable === 'boolean' && ['id', 'name', 'type'].every(k => typeof c[k] === 'string')) &&
      ['primaryKey', 'unique', 'indexes', 'checks', 'foreignKeys'].every(k => Array.isArray(t[k])) &&
      t.primaryKey.every(id => typeof id === 'string') && t.foreignKeys.every(f => f && typeof f.tableId === 'string' &&
        ['columns', 'targetColumns'].every(k => Array.isArray(f[k]) && f[k].every(id => typeof id === 'string')) &&
        ['onDelete', 'onUpdate'].every(k => typeof f[k] === 'string')))
}
export default function SchemaEditor({ capability, active = true }) {
  const [draft, setDraft] = useState(() => clone(sample))
  const [source, setSource] = useState('draft')
  const [sampleTarget, setSampleTarget] = useState('sqlite')
  const [tab, setTab] = useState('Tables')
  const [tableIndex, setTableIndex] = useState(0)
  const [json, setJson] = useState(() => text(sample))
  const [unsaved, setUnsaved] = useState(false)
  const [result, setResult] = useState(null)
  const [findings, setFindings] = useState([])
  const [file, setFile] = useState('schema.sql')
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState('Edit your schema or inspect the separate sample. Generate validates before rendering.')
  const guard = useRef(revisionGuard()), pending = useRef(null)
  const request = source === 'sample' ? { ...sample, target: sampleTarget } : draft
  const selected = request.tables[Math.min(tableIndex, request.tables.length - 1)]
  const readonly = source === 'sample'
  const activeFile = result?.files.find(f => f.path === file)
  function invalidate(message = 'Schema changed. Generate again before downloading.') {
    guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null); setFindings([]); setStatus(message)
  }
  useEffect(() => () => { guard.current.edit(); pending.current?.abort() }, [])
  useEffect(() => { invalidate('Access changed. Generate again to check current access.') }, [capability?.canGenerate])
  useEffect(() => { invalidate('Module changed. Generate again before downloading.') }, [active])
  function edit(update) {
    invalidate(); const next = clone(draft); update(next); setDraft(next); setJson(text(next)); setUnsaved(false)
  }
  function editTable(update) { edit(next => update(next.tables[Math.min(tableIndex, next.tables.length - 1)])) }
  function applyJson() {
    try {
      const next = JSON.parse(json)
      if (!editable(next)) throw new Error('shape')
      setDraft(next); setUnsaved(false); setTableIndex(0); invalidate('JSON applied to forms. Generate runs full validation.')
    } catch { setStatus('JSON is invalid or lacks table editing fields. Draft preserved; correct it or explicitly discard changes.') }
  }
  async function generate() {
    const raw = source === 'sample' ? text(request) : json
    if (new TextEncoder().encode('{"request":' + raw + '}').length > 1048576) { setStatus('Request exceeds 1 MiB. Reduce the schema.'); return }
    pending.current?.abort(); const controller = new AbortController(); pending.current = controller
    const ticket = guard.current.start(); setBusy(true); setResult(null); setFindings([]); setStatus('Validating and generating schema…')
    try {
      const { data } = await generateBackendSchema(raw, controller.signal)
      if (!guard.current.current(ticket)) return
      setFindings(data?.data?.findings || [])
      if (data?.success) {
        setResult({ ...data.data, requestText: raw }); setFile('schema.sql'); setStatus('Schema validated and generated. Review the files and limitations before downloading.')
      } else setStatus(`Generation rejected (${data?.errorCode || 'REQUEST_FAILED'}). Your input is preserved.`)
    } catch (error) { if (guard.current.current(ticket) && error.name !== 'AbortError') setStatus('Generation network failure. Input preserved; retry.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  async function downloadBundle() {
    if (!result) return
    const snapshot = result, ticket = guard.current.start(), controller = new AbortController()
    pending.current?.abort(); pending.current = controller; setBusy(true); setStatus('Revalidating and preparing bundle…')
    try {
      const response = await exportBackendSchema(snapshot.requestText, snapshot.digest, controller.signal)
      if (!guard.current.current(ticket)) return
      if (response.blob) { save(response.blob, 'usefultools-schema.zip'); setStatus('Bundle downloaded. Payload hashes are recorded in manifest.json.') }
      else setStatus(`Export failed (${response.data?.errorCode || 'TRANSPORT_ERROR'}). Input and preview preserved; retry.`)
    } catch (error) { if (guard.current.current(ticket) && error.name !== 'AbortError') setStatus('Export network failure. Input and preview preserved; retry.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  async function copy() {
    const ticket = guard.current.start()
    try { await navigator.clipboard.writeText(activeFile.content); if (guard.current.current(ticket)) setStatus('File copied.') }
    catch { if (guard.current.current(ticket)) setStatus('Clipboard unavailable. Select the file text or download it instead.') }
  }
  return <section aria-label="Schema generator" hidden={!active}>
    <h2>Schema generator</h2>
    <p>Contract {capability?.schemaVersion} · template {capability?.templateVersion}. Initial schema creation only.</p>
    {!capability?.canGenerate && <p>Generation requires a registered account and enabled access. Admin preview is available only when explicitly disabled.</p>}
    <div className={styles.controls}>
      <label>Schema source<select value={source} disabled={unsaved} onChange={e => { invalidate(); setSource(e.target.value); setTableIndex(0) }}><option value="draft">Personal draft</option><option value="sample">Read-only customers/orders sample</option></select></label>
      <label>Schema dialect<select value={request.target} disabled={unsaved} onChange={e => readonly ? (invalidate(), setSampleTarget(e.target.value)) : edit(n => { n.target = e.target.value })}>{capability?.targets.map(t => <option key={t} value={t}>{t}</option>)}</select></label>
      <label>Generator template<select value={request.templateVersion} disabled={unsaved || readonly} onChange={e => edit(n => { n.templateVersion = e.target.value })}><option value={capability?.templateVersion}>{capability?.templateVersion}</option></select></label>
    </div>
    <div className={styles.actions} role="group" aria-label="Schema sections">{['Tables', 'Relationships', 'Options', 'JSON'].map(t => <button type="button" key={t} aria-pressed={tab === t} disabled={unsaved && t !== 'JSON'} onClick={() => setTab(t)}>{t}</button>)}</div>
    {tab !== 'JSON' && <label>Selected table<select value={Math.min(tableIndex, request.tables.length - 1)} onChange={e => setTableIndex(Number(e.target.value))}>{request.tables.map((t, i) => <option key={`${t.id}-${i}`} value={i}>{t.name} ({t.id})</option>)}</select></label>}
    {tab === 'Tables' && <>
      <p>Internal IDs remain stable when database names change. Use JSON for advanced type options and typed defaults.</p>
      <fieldset disabled={readonly}><legend>Table definition</legend>
        <label>Table database name<input value={selected.name} maxLength={63} onChange={e => editTable(t => { t.name = e.target.value })} /></label>
        <p>Stable table ID: <code>{selected.id}</code></p>
        {selected.columns.map((c, i) => <fieldset key={`${c.id}-${i}`}><legend>Column {c.id}</legend>
          <label>Column database name<input value={c.name} maxLength={63} onChange={e => editTable(t => { t.columns[i].name = e.target.value })} /></label>
          <label>Logical type<select value={c.type} onChange={e => editTable(t => {
            const col = t.columns[i]; col.type = e.target.value; delete col.precision; delete col.scale; delete col.length; delete col.default
            if (col.type === 'decimal') { col.precision = 12; col.scale = 2 }
            if (col.type === 'varchar') col.length = 80
          })}>{types.map(v => <option key={v}>{v}</option>)}</select></label>
          <label><input type="checkbox" checked={c.nullable} onChange={e => editTable(t => { t.columns[i].nullable = e.target.checked })} /> Nullable</label>
          <label><input type="checkbox" checked={selected.primaryKey.includes(c.id)} onChange={e => editTable(t => { t.primaryKey = e.target.checked ? [...t.primaryKey, c.id] : t.primaryKey.filter(id => id !== c.id) })} /> Primary key (selection order)</label>
          <button type="button" onClick={() => editTable(t => { t.columns.splice(i, 1) })}>Remove column {c.id}</button>
        </fieldset>)}
        <button type="button" disabled={selected.columns.length >= 100} onClick={() => editTable(t => {
          let id = 'column_' + (t.columns.length + 1); while (t.columns.some(c => c.id === id)) id += '_'
          t.columns.push({ id, name: id, type: 'text', nullable: true })
        })}>Add column</button>
        <button type="button" disabled={draft.tables.length >= 100} onClick={() => edit(n => {
          let id = 'table_' + (n.tables.length + 1); while (n.tables.some(t => t.id === id)) id += '_'
          n.tables.push({ id, name: id, columns: [{ id: 'id', name: 'id', type: 'integer', nullable: false }], primaryKey: ['id'], unique: [], indexes: [], checks: [], foreignKeys: [] })
          setTableIndex(n.tables.length - 1)
        })}>Add table</button>
        <button type="button" disabled={draft.tables.length <= 1} onClick={() => { edit(n => { n.tables.splice(tableIndex, 1) }); setTableIndex(0) }}>Remove table</button>
      </fieldset>
    </>}
    {tab === 'Relationships' && <fieldset disabled={readonly}><legend>Foreign keys</legend>
      <p>References use IDs and survive database renames. Composite keys use ordered comma-separated IDs. Deleting a referenced column produces a blocking server finding.</p>
      {selected.foreignKeys.map((f, i) => <fieldset key={i}><legend>Relationship {i + 1}</legend>
        <label>Local column IDs<input value={f.columns.join(',')} onChange={e => editTable(t => { t.foreignKeys[i].columns = e.target.value.split(',').map(v => v.trim()) })} /></label>
        <label>Referenced table ID<select value={f.tableId} onChange={e => editTable(t => { t.foreignKeys[i].tableId = e.target.value })}>{request.tables.map(t => <option key={t.id} value={t.id}>{t.name} ({t.id})</option>)}</select></label>
        <label>Referenced column IDs<input value={f.targetColumns.join(',')} onChange={e => editTable(t => { t.foreignKeys[i].targetColumns = e.target.value.split(',').map(v => v.trim()) })} /></label>
        {['onDelete', 'onUpdate'].map(key => <label key={key}>{key}<select value={f[key]} onChange={e => editTable(t => { t.foreignKeys[i][key] = e.target.value })}>{['NO ACTION', 'RESTRICT', 'CASCADE', 'SET NULL'].map(v => <option key={v}>{v}</option>)}</select></label>)}
        <button type="button" onClick={() => editTable(t => { t.foreignKeys.splice(i, 1) })}>Remove relationship {i + 1}</button>
      </fieldset>)}
      <button type="button" onClick={() => editTable(t => { t.foreignKeys.push({ columns: [t.columns[0]?.id || 'id'], tableId: request.tables[0].id, targetColumns: [...request.tables[0].primaryKey], onDelete: 'NO ACTION', onUpdate: 'NO ACTION' }) })}>Add relationship</button>
    </fieldset>}
    {tab === 'Options' && <>
      <p>Unique keys, indexes, checks and typed defaults are edited in the synchronized JSON section. Column comparisons support eq, ne, lt, le, gt, ge; raw SQL is rejected.</p>
      <pre>{text({ primaryKey: selected.primaryKey, unique: selected.unique, indexes: selected.indexes, checks: selected.checks })}</pre>
      <button type="button" onClick={() => setTab('JSON')}>Edit advanced options in JSON</button>
      <p>SQLite decimal is approximate; date/timestamp TEXT formats are conventions. Boolean, varchar length and JSON validity are checked. PostgreSQL uses NUMERIC, TIMESTAMPTZ and JSONB. Multi-table cycles and migrations are unavailable.</p>
    </>}
    {tab === 'JSON' && <>
      <p>JSON changes remain separate until Apply. Forms and source selection stay locked while changes are unapplied. Generate can validate this exact JSON, including duplicate-key rejection.</p>
      <label>Schema generation JSON<textarea rows={18} readOnly={readonly} maxLength={1048576} spellCheck={false} value={readonly ? text(request) : json} onChange={e => { invalidate(); setJson(e.target.value); setUnsaved(true) }} /></label>
      {!readonly && <div className={styles.actions}><button type="button" disabled={!unsaved} onClick={applyJson}>Apply JSON to forms</button><button type="button" disabled={!unsaved} onClick={() => { invalidate(); setJson(text(draft)); setUnsaved(false) }}>Discard JSON changes</button></div>}
    </>}
    <p>Your schema is sent to UsefulTools for validation and rendering. It is not saved in browser storage or retained as a project. Do not include credentials or personal data. The server does not execute generated SQL.</p>
    <div className={styles.actions}>
      <button type="button" disabled={busy || !capability?.canGenerate} onClick={generate}>{busy ? 'Working…' : 'Validate and generate schema'}</button>
      {busy && <button type="button" onClick={() => invalidate('Request cancelled. Input preserved.')}>Cancel generation request</button>}
      <button type="button" disabled={busy || !result || !capability?.canExport} onClick={downloadBundle}>Download bundle</button>
    </div>
    <p role="status" aria-live="polite" aria-atomic="true">{status}</p>
    {findings.length > 0 && <section aria-label="Generation findings"><h3>Generation findings</h3><ol>{findings.map((f, i) => <li key={i}><strong>{f.blocking ? 'Blocking' : 'Advisory'}: {f.ruleId}</strong> <code>{f.path}</code><p>{f.message} {f.suggestion}</p></li>)}</ol></section>}
    {result && <section aria-label="Generated files"><h3>Generated files</h3><p>{result.manifest.core.target} · {result.manifest.core.templateVersion}</p>
      <label>Preview file<select value={file} onChange={e => setFile(e.target.value)}>{result.files.map(f => <option key={f.path}>{f.path}</option>)}</select></label>
      <label>Generated file content<textarea rows={20} readOnly value={activeFile?.content || ''} spellCheck={false} /></label>
      <div className={styles.actions}><button type="button" disabled={!activeFile || busy} onClick={copy}>Copy file</button><button type="button" disabled={!activeFile || busy} onClick={() => save(new Blob([activeFile.content], { type: 'text/plain;charset=utf-8' }), activeFile.path)}>Download file</button></div>
    </section>}
  </section>
}
