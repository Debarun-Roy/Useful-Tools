import { useEffect, useRef, useState } from 'react'
import { generateBackendSchema, exportBackendSchema } from '../../api/apiClient'
import { revisionGuard } from './revision'
import sample from './etlSample.json'
import styles from './BackendSupportPage.module.css'

const text = value => JSON.stringify(value, null, 2)
function editable(s) {
  return s?.module === 'etl' && s.schema?.tables?.length > 0 && s.schema.tables.every(t => typeof t?.id === 'string' && typeof t.name === 'string' && Array.isArray(t.columns) && t.columns.every(c => typeof c?.id === 'string' && typeof c.name === 'string') && Array.isArray(t.primaryKey) && Array.isArray(t.unique)) && s.source && ['format', 'encoding', 'delimiter', 'unexpectedFields'].every(k => typeof s.source[k] === 'string') && Array.isArray(s.mappings) && s.mappings.every(m => m && ['source', 'columnId', 'missing', 'null', 'empty'].every(k => typeof m[k] === 'string') && Array.isArray(m.transforms) && m.transforms.every(t => typeof t === 'string')) && ['conflictKey', 'updateColumns'].every(k => Array.isArray(s[k]) && s[k].every(v => typeof v === 'string')) && s.runtime && ['sourceEnv', 'connectionEnv', 'checkpointEnv', 'rejectsEnv'].every(k => typeof s.runtime[k] === 'string') && s.rejects && typeof s.rejects.maxBytes === 'number'
}
function Select({ label, value, values, onChange }) {
  return <label>{label}<select aria-label={label} value={value} onChange={e => onChange(e.target.value)}>{values.map(v => <option key={v} value={v}>{v === '\t' ? 'tab' : v}</option>)}</select></label>
}
export default function EtlEditor({ capability, active }) {
  const [draft, setDraft] = useState(() => structuredClone(sample)), [json, setJson] = useState(() => text(sample))
  const [unapplied, setUnapplied] = useState(false), [busy, setBusy] = useState(false), [result, setResult] = useState(null)
  const [findings, setFindings] = useState([]), [file, setFile] = useState(''), [status, setStatus] = useState('Configure a local ETL job. Drafts stay in memory only.')
  const guard = useRef(revisionGuard()), pending = useRef(null), editor = useRef(null)
  function invalidate(message = 'Draft changed. Generate a fresh preview before export.') { guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null); setFindings([]); setStatus(message) }
  useEffect(() => () => { guard.current.edit(); pending.current?.abort() }, [])
  useEffect(() => { invalidate('Module or access changed. Generate again to check current access.') }, [active, capability?.canGenerate, capability?.canExport])
  function edit(update) { invalidate(); const next = structuredClone(draft); update(next); setDraft(next); setJson(text(next)); setUnapplied(false) }
  function apply() {
    try { const next = JSON.parse(json); if (!editable(next)) throw new Error('shape'); setDraft(next); setUnapplied(false); invalidate('JSON applied. The server validates all semantics before export.') }
    catch { setStatus('Invalid JSON or missing guided fields. Input preserved; correct it or explicitly discard changes.') }
  }
  async function generate() {
    if (new TextEncoder().encode('{"request":' + json + '}').length > 1048576) { setStatus('Request exceeds 1 MiB. Reduce the specification.'); return }
    pending.current?.abort(); const controller = new AbortController(); pending.current = controller; const ticket = guard.current.start()
    setBusy(true); setResult(null); setFindings([]); setStatus('Validating ETL specification…')
    try {
      const { data } = await generateBackendSchema(json, controller.signal)
      if (!guard.current.current(ticket)) return
      setFindings(data?.data?.findings || [])
      if (data?.success) { setResult({ ...data.data, requestText: json }); setFile('README.md'); setStatus('ETL bundle ready for review. Generation does not run a job or connect to a database.') }
      else setStatus(`Generation blocked (${data?.errorCode || 'REQUEST_FAILED'}). Input preserved.`)
    } catch (e) { if (guard.current.current(ticket) && e.name !== 'AbortError') setStatus('Network failure. Input preserved; retry.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  async function download() {
    if (!result) return
    const snapshot = result, ticket = guard.current.start(), controller = new AbortController(); pending.current?.abort(); pending.current = controller; setBusy(true)
    try {
      const response = await exportBackendSchema(snapshot.requestText, snapshot.digest, controller.signal)
      if (!guard.current.current(ticket)) return
      if (response.blob) {
        const url = URL.createObjectURL(response.blob), a = document.createElement('a'); a.href = url; a.download = 'usefultools-etl.zip'; document.body.appendChild(a)
        try { a.click() } finally { a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
        setStatus('ETL bundle downloaded after revalidation. Follow its README in an isolated local environment.')
      } else setStatus(`Export failed (${response.data?.errorCode || 'TRANSPORT_ERROR'}). Input preserved.`)
    } catch (e) { if (guard.current.current(ticket) && e.name !== 'AbortError') setStatus('Export network failure. Input preserved.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  const table = draft.schema.tables.find(t => t.id === draft.tableId), columns = table?.columns || []
  const keys = table ? [table.primaryKey, ...table.unique].filter(k => k.length) : []
  const activeFile = result?.files.find(f => f.path === file)
  async function copyArtifact() {
    if (!activeFile) return
    try { await navigator.clipboard.writeText(activeFile.content); setStatus('Artifact copied.') }
    catch { setStatus('Clipboard unavailable. Select and copy the preview text; the draft is preserved.') }
  }
  return <section aria-label="Python ETL generator" hidden={!active}>
    <h2>Python ETL generator</h2>
    <p>Local CSV or JSON array to one existing SQLite or PostgreSQL table. Contract {capability?.schemaVersion} · template {capability?.templateVersion}. Do not enter dataset rows or credentials here.</p>
    {!capability?.canGenerate && <p>Generation requires registered access and an enabled tool, or disabled-tool admin preview.</p>}
    <fieldset disabled={unapplied}><legend>ETL guided configuration</legend>
      <div className={styles.controls}>
        <Select label="ETL destination" value={draft.target} values={['sqlite', 'postgresql']} onChange={v => edit(n => { n.target = v; n.schema.target = v })} />
        <Select label="Source format" value={draft.source.format} values={['csv', 'json']} onChange={v => edit(n => { n.source.format = v })} />
        <Select label="Source encoding" value={draft.source.encoding} values={['utf-8', 'utf-8-sig']} onChange={v => edit(n => { n.source.encoding = v })} />
        <Select label="CSV delimiter" value={draft.source.delimiter} values={[',', ';', '\t']} onChange={v => edit(n => { n.source.delimiter = v })} />
        <Select label="Unexpected source fields" value={draft.source.unexpectedFields} values={['reject', 'ignore']} onChange={v => edit(n => { n.source.unexpectedFields = v })} />
        <label>Destination table<select value={draft.tableId} onChange={e => edit(n => { n.tableId = e.target.value; n.mappings = []; n.conflictKey = []; n.updateColumns = [] })}>{draft.schema.tables.map((t, i) => <option key={i} value={t.id}>{t.name}</option>)}</select></label>
      </div>
      <p>CSV uses double quotes and doubled quote escaping. JSON must be a bounded top-level array. CSV limit 16 MiB; JSON limit 2 MiB. Destination schema is edited in the full JSON below; changing tables clears mappings explicitly.</p>
      {draft.mappings.map((m, i) => <fieldset key={i}><legend>Mapping {i + 1}</legend><div className={styles.controls}>
        <label>Source field {i + 1}<input maxLength={128} value={m.source} onChange={e => edit(n => { n.mappings[i].source = e.target.value })} /></label>
        <label>Target column {i + 1}<select value={m.columnId} onChange={e => edit(n => { n.mappings[i].columnId = e.target.value })}><option value="" disabled>Select column</option>{columns.map((c, j) => <option key={j} value={c.id}>{c.name} ({c.type})</option>)}</select></label>
        {['missing', 'null', 'empty'].map(policy => <Select key={policy} label={`${policy} policy ${i + 1}`} value={m[policy]} values={policy === 'empty' ? ['reject', 'null', 'default', 'keep'] : ['reject', 'null', 'default']} onChange={v => edit(n => { n.mappings[i][policy] = v })} />)}
      </div>
        <p>Transforms execute top to bottom. Finish with the matching conversion; the server rejects incompatible ordering.</p>
        {m.transforms.map((t, j) => <div className={styles.actions} key={j}><Select label={`Transform ${i + 1}.${j + 1}`} value={t} values={['trim', 'lower', 'upper', 'cast', 'date_iso', 'date_dmy', 'timestamp_iso']} onChange={v => edit(n => { n.mappings[i].transforms[j] = v })} /><button type="button" disabled={j === 0} onClick={() => edit(n => { const a = n.mappings[i].transforms; [a[j - 1], a[j]] = [a[j], a[j - 1]] })}>Move transform up</button><button type="button" onClick={() => edit(n => n.mappings[i].transforms.splice(j, 1))}>Remove transform</button></div>)}
        <button type="button" disabled={m.transforms.length >= 5} onClick={() => edit(n => n.mappings[i].transforms.push('cast'))}>Add transform</button>
        <button type="button" onClick={() => edit(n => n.mappings.splice(i, 1))}>Remove mapping {i + 1}</button>
      </fieldset>)}
      <button type="button" disabled={!columns.length || draft.mappings.length >= 100} onClick={() => edit(n => n.mappings.push({ source: '', columnId: columns[0].id, transforms: ['cast'], missing: 'reject', null: 'reject', empty: 'reject' }))}>Add mapping</button>
      <div className={styles.controls}>
        <Select label="Write mode" value={draft.mode} values={['insert', 'upsert']} onChange={v => edit(n => { n.mode = v; n.resumePolicy = v === 'insert' ? 'manual_insert' : 'upsert_replay'; n.conflictKey = []; n.updateColumns = [] })} />
        <Select label="Error policy" value={draft.errorMode} values={['strict', 'continue']} onChange={v => edit(n => { n.errorMode = v })} />
        <label>Batch size<input type="number" min="1" max="1000" value={draft.batchSize} onChange={e => edit(n => { n.batchSize = Number(e.target.value) })} /></label>
        <Select label="Naive timestamp policy" value={draft.naiveTimestamp} values={['reject', 'utc']} onChange={v => edit(n => { n.naiveTimestamp = v })} />
        <label>Reject report maximum bytes<input type="number" min="256" max="1048576" value={draft.rejects.maxBytes} onChange={e => edit(n => { n.rejects.maxBytes = Number(e.target.value) })} /></label>
      </div>
      {draft.mode === 'upsert' && <fieldset><legend>Explicit conflict policy</legend><label>Conflict key<select value={JSON.stringify(draft.conflictKey)} onChange={e => edit(n => { n.conflictKey = JSON.parse(e.target.value) })}><option value="[]">Choose primary or unique key</option>{keys.map((k, i) => <option key={i} value={JSON.stringify(k)}>{k.join(' + ')}</option>)}</select></label><p>Select mapped non-key columns to update. Unselected columns remain unchanged on conflict.</p>{columns.map((c, i) => <label key={i}><span><input type="checkbox" checked={draft.updateColumns.includes(c.id)} onChange={e => edit(n => { n.updateColumns = e.target.checked ? [...n.updateColumns, c.id] : n.updateColumns.filter(id => id !== c.id) })} /> Update {c.name}</span></label>)}</fieldset>}
      <fieldset><legend>Runtime environment variable names</legend><div className={styles.controls}>{Object.keys(sample.runtime).map(key => <label key={key}>{key}<input value={draft.runtime[key]} maxLength={64} onChange={e => edit(n => { n.runtime[key] = e.target.value })} /></label>)}</div></fieldset>
      <p>Resume policy: {draft.resumePolicy}. External checkpoints are written after commit; a crash in between can replay rows. Insert resume requires explicit acknowledgement and reconciliation. Upsert replay can repeat trigger or other side effects. No exactly-once guarantee.</p>
      <p>Run the generated program with --dry-run first: no credentials or database writes, and no checkpoint changes. Dry run cannot prove destination constraints. Strict stops and rolls back the current batch; continue records row rejects with savepoints. Exit codes and bounded diagnostics are documented in the bundle.</p>
    </fieldset>
    <label>ETL specification JSON<textarea ref={editor} rows={16} spellCheck={false} value={json} onChange={e => { setJson(e.target.value); setUnapplied(true); invalidate('Unapplied JSON edits preserved. Apply or discard before guided editing.') }} /></label>
    <div className={styles.actions}><button type="button" disabled={!unapplied} onClick={apply}>Apply ETL JSON</button><button type="button" disabled={!unapplied} onClick={() => { setJson(text(draft)); setUnapplied(false); invalidate('Unapplied edits discarded explicitly.') }}>Discard ETL JSON changes</button><button type="button" disabled={busy || !capability?.canGenerate} onClick={generate}>Generate ETL preview</button><button type="button" disabled={busy || !result || !capability?.canExport} onClick={download}>Download ETL ZIP</button></div>
    <p role="status" aria-live="polite">{status}</p>
    {findings.length > 0 && <ul>{findings.map((f, i) => <li key={i}><button type="button" onClick={() => { editor.current?.focus(); setStatus(`Correct ${f.path} in the preserved JSON.`) }}>{f.ruleId} · {f.path}</button></li>)}</ul>}
    {result && <><p>Bundle digest: <code>{result.digest}</code></p><label>ETL preview file<select value={file} onChange={e => setFile(e.target.value)}>{result.files.map(f => <option key={f.path}>{f.path}</option>)}</select></label><button type="button" onClick={copyArtifact}>Copy ETL artifact</button><pre aria-label="ETL artifact content">{activeFile?.content}</pre></>}
  </section>
}
