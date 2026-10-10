import { useEffect, useRef, useState } from 'react'
import { generateBackendSchema, exportBackendSchema } from '../../api/apiClient'
import { revisionGuard } from './revision'
import migrationSample from './migrationSample.json'
import viewSample from './viewSample.json'
import evaluatorSample from './evaluatorSample.json'
import styles from './BackendSupportPage.module.css'

const samples = { migration: migrationSample, view: viewSample, evaluator: evaluatorSample }
const titles = { migration: 'Migration planner', view: 'View generator', evaluator: 'Schema evaluator' }
const text = value => JSON.stringify(value, null, 2)
const ref = (sourceAlias, columnId) => ({ kind: 'column', sourceAlias, columnId })
function editable(s, module) {
  const schema = value => value && Array.isArray(value.tables) && value.tables.every(t => t && typeof t.id === 'string' && typeof t.name === 'string' && Array.isArray(t.columns) && t.columns.every(c => c && typeof c.id === 'string' && typeof c.name === 'string') && ['primaryKey', 'unique', 'indexes', 'checks', 'foreignKeys'].every(k => Array.isArray(t[k])))
  if (!s || s.module !== module || typeof s.target !== 'string') return false
  if (module === 'migration') return schema(s.before) && schema(s.after) && s.renames && ['tables', 'columns'].every(k => Array.isArray(s.renames[k]))
  if (!schema(s.schema)) return false
  if (module === 'evaluator') return s.schema.tables.every(t => Array.isArray(t.businessKeys))
  return typeof s.name === 'string' && s.source && typeof s.source.alias === 'string' && Array.isArray(s.joins) && s.joins.every(j => j?.source && j.left && j.right) && Array.isArray(s.projections) && s.projections.every(p => p?.expression && typeof p.name === 'string') && Array.isArray(s.groupBy)
}
function ColumnSelect({ label, value, sources, onChange }) {
  return <label>{label}<select value={value ? `${value.sourceAlias}/${value.columnId}` : ''} onChange={e => { const [alias, id] = e.target.value.split('/'); onChange(ref(alias, id)) }}><option value="" disabled>Select column</option>{sources.flatMap(({ alias, table }) => (table?.columns || []).map(c => <option key={`${alias}/${c.id}`} value={`${alias}/${c.id}`}>{alias} · {c.name}</option>))}</select></label>
}

export default function B3Editor({ module, capability, active }) {
  const [draft, setDraft] = useState(() => structuredClone(samples[module]))
  const [json, setJson] = useState(() => text(samples[module]))
  const [unapplied, setUnapplied] = useState(false)
  const [result, setResult] = useState(null), [findings, setFindings] = useState([]), [details, setDetails] = useState(null)
  const [status, setStatus] = useState('Configure the sample as a starting draft. Nothing is saved in browser storage.')
  const [busy, setBusy] = useState(false), [file, setFile] = useState(''), [severity, setSeverity] = useState('all')
  const [tableIndex, setTableIndex] = useState(0), [columnId, setColumnId] = useState(''), [newName, setNewName] = useState(''), [defaultValue, setDefaultValue] = useState('')
  const [literalText, setLiteralText] = useState(null)
  const guard = useRef(revisionGuard()), pending = useRef(null), editor = useRef(null)
  function invalidate(message = 'Draft changed. Run again before downloading.') { guard.current.edit(); pending.current?.abort(); setBusy(false); setResult(null); setFindings([]); setDetails(null); setStatus(message) }
  useEffect(() => () => { guard.current.edit(); pending.current?.abort() }, [])
  useEffect(() => { invalidate('Module or access changed. Run again to check current access.') }, [active, capability?.canGenerate])
  function edit(update) { invalidate(); const next = structuredClone(draft); update(next); setDraft(next); setJson(text(next)); setUnapplied(false) }
  function apply() {
    try { const next = JSON.parse(json); if (!editable(next, module)) throw new Error('shape'); setDraft(next); setUnapplied(false); setTableIndex(0); invalidate('JSON applied. Server validation still runs before export.') }
    catch { setStatus('JSON is invalid or lacks guided editing fields. Input preserved. Correct it or explicitly discard changes.') }
  }
  async function generate() {
    if (new TextEncoder().encode('{"request":' + json + '}').length > 1048576) { setStatus('Request exceeds 1 MiB. Reduce the input.'); return }
    pending.current?.abort(); const controller = new AbortController(); pending.current = controller; const ticket = guard.current.start()
    setBusy(true); setResult(null); setFindings([]); setDetails(null); setStatus(module === 'evaluator' ? 'Evaluating schema…' : 'Validating complete specification…')
    try {
      const { data } = await generateBackendSchema(json, controller.signal)
      if (!guard.current.current(ticket)) return
      const value = data?.data; setFindings(value?.details?.findings || value?.findings || []); setDetails(value?.details || null)
      if (data?.success) { setResult({ ...value, requestText: json }); setFile(value.files[0].path); setStatus(module === 'evaluator' ? `Evaluation completed${value.details.hasErrors ? ' with errors' : ''}. Report export is available; this does not establish schema validity.` : 'Complete supported plan ready. Review files, preconditions and limitations before export.') }
      else setStatus(`Blocked (${data?.errorCode || 'REQUEST_FAILED'}). No executable bundle is available. Your input is preserved.`)
    } catch (e) { if (guard.current.current(ticket) && e.name !== 'AbortError') setStatus('Network failure. Input preserved; retry.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  async function download() {
    if (!result) return
    const snapshot = result, ticket = guard.current.start(), controller = new AbortController(); pending.current?.abort(); pending.current = controller; setBusy(true)
    try {
      const response = await exportBackendSchema(snapshot.requestText, snapshot.digest, controller.signal)
      if (!guard.current.current(ticket)) return
      if (response.blob) { const url = URL.createObjectURL(response.blob), a = document.createElement('a'); a.href = url; a.download = `usefultools-${module}.zip`; document.body.appendChild(a); try { a.click() } finally { a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }; setStatus('Bundle downloaded after independent revalidation. Inspect manifest.json for hashes.') }
      else setStatus(`Export failed (${response.data?.errorCode || 'TRANSPORT_ERROR'}). Input and preview preserved.`)
    } catch (e) { if (guard.current.current(ticket) && e.name !== 'AbortError') setStatus('Export network failure. Input and preview preserved.') }
    finally { if (guard.current.current(ticket)) setBusy(false) }
  }
  const tables = module === 'migration' ? draft.after.tables : draft.schema.tables
  const ti = Math.min(tableIndex, Math.max(0, tables.length - 1)), table = tables[ti]
  const sources = module === 'view' ? [draft.source, ...draft.joins.map(j => j.source)].map(s => ({ alias: s.alias, table: tables.find(t => t.id === s.tableId) })) : []
  const activeFile = result?.files.find(f => f.path === file)
  function navigate(path) { editor.current?.focus(); setStatus(`Finding at ${path}. Edit this field in the full specification JSON and apply it.`) }
  return <section aria-label={titles[module]} hidden={!active}>
    <h2>{titles[module]}</h2>
    <p>Contract {capability?.schemaVersion} · template {capability?.templateVersion}. {module === 'evaluator' ? 'Diagnostics never execute SQL.' : 'SQL is exported for operator review; UsefulTools never connects to your database.'}</p>
    {!capability?.canGenerate && <p>Generation and report export require registered access and an enabled tool, or explicit disabled admin preview.</p>}
    <fieldset disabled={unapplied}><legend>Guided configuration</legend>
      <label>{module} dialect<select value={draft.target} onChange={e => edit(n => { n.target = e.target.value; for (const key of ['before', 'after', 'schema']) if (n[key]?.target) n[key].target = e.target.value })}><option>sqlite</option><option>postgresql</option></select></label>
      {module === 'migration' && <>
        <p>100 tables per snapshot; combined maximum 1,000 columns. Appended nullable or constant-default columns, ordinary indexes and explicit renames are supported. Drops and all other changes block the complete plan, even after acknowledgement.</p>
        <label>Migration table<select value={ti} onChange={e => { setTableIndex(Number(e.target.value)); setColumnId('') }}>{tables.map((t, i) => <option key={i} value={i}>{t.name}</option>)}</select></label>
        <div className={styles.controls}><label>New column ID / name<input value={newName} onChange={e => setNewName(e.target.value)} maxLength={63} /></label><label>Optional required text default<input value={defaultValue} onChange={e => setDefaultValue(e.target.value)} maxLength={4096} /></label></div>
        <button type="button" disabled={!table || !newName} onClick={() => edit(n => { const c = { id: newName, name: newName, type: 'text', nullable: !defaultValue }; if (defaultValue) c.default = { kind: 'literal', value: defaultValue }; n.after.tables[ti].columns.push(c) })}>Append column</button>
        <label>Existing column<select value={columnId} onChange={e => setColumnId(e.target.value)}><option value="">Choose column</option>{table?.columns.map((c, i) => <option key={i} value={c.id}>{c.name}</option>)}</select></label>
        <div className={styles.actions}>
          <button type="button" disabled={!table || !columnId || !newName} onClick={() => edit(n => n.after.tables[ti].indexes.push({ id: newName, name: newName, columns: [columnId], unique: false }))}>Add ordinary index</button>
          <button type="button" disabled={!table || !newName || !draft.before.tables.some(t => t.id === table.id)} onClick={() => edit(n => { const t = n.after.tables[ti], old = n.before.tables.find(b => b.id === t.id); n.renames.tables = n.renames.tables.filter(r => r.tableId !== t.id); n.renames.tables.push({ tableId: t.id, fromName: old.name, toName: newName }); t.name = newName })}>Map table rename</button>
          <button type="button" disabled={!table || !newName || !columnId || !draft.before.tables.find(t => t.id === table.id)?.columns.some(c => c.id === columnId)} onClick={() => edit(n => { const t = n.after.tables[ti], c = t.columns.find(c => c.id === columnId), old = n.before.tables.find(b => b.id === t.id).columns.find(c => c.id === columnId); n.renames.columns = n.renames.columns.filter(r => !(r.tableId === t.id && r.columnId === c.id)); n.renames.columns.push({ tableId: t.id, columnId: c.id, fromName: old.name, toName: newName }); c.name = newName })}>Map column rename</button>
        </div>
        <pre aria-label="Explicit rename mappings">{text(draft.renames)}</pre>
        <div className={styles.controls}>{['before', 'after'].map(key => <label key={key}>{key} snapshot (read-only; edit full JSON below)<textarea readOnly rows={10} value={text(draft[key])} /></label>)}</div>
        <label><span><input type="checkbox" checked={draft.externalDependenciesReviewed} onChange={e => edit(n => { n.externalDependenciesReviewed = e.target.checked })} /> External dependencies reviewed; maintenance window and backup required</span></label>
        <label><span><input type="checkbox" checked={draft.acknowledgeDestructive} onChange={e => edit(n => { n.acknowledgeDestructive = e.target.checked })} /> I acknowledge destructive warnings (does not enable unsupported operations)</span></label>
        <p>Run the generated migrate.py procedure, which locks, checks, applies and checks again in one transaction. Column additions have no automatic down.sql because later writes could be lost. Review CHECK, default, FK and collation equivalence manually.</p>
      </>}
      {module === 'view' && <>
        <label>View name<input value={draft.name} maxLength={63} onChange={e => edit(n => { n.name = e.target.value })} /></label>
        <div className={styles.controls}><label>Source table<select value={draft.source.tableId} onChange={e => edit(n => { n.source.tableId = e.target.value })}>{tables.map((t, i) => <option key={i} value={t.id}>{t.name}</option>)}</select></label><label>Source alias<input value={draft.source.alias} maxLength={63} onChange={e => edit(n => { n.source.alias = e.target.value })} /></label></div>
        <p>Changing aliases does not guess reference updates. Update affected references below or in JSON. Tables must already exist.</p>
        {draft.joins.map((j, i) => <fieldset key={i}><legend>Join {i + 1}</legend><label>Join type<select value={j.type} onChange={e => edit(n => { n.joins[i].type = e.target.value })}><option value="inner">INNER</option><option value="left">LEFT</option></select></label><label>Joined table<select value={j.source.tableId} onChange={e => edit(n => { n.joins[i].source.tableId = e.target.value })}>{tables.map((t, k) => <option key={k} value={t.id}>{t.name}</option>)}</select></label><label>Joined alias<input value={j.source.alias} onChange={e => edit(n => { n.joins[i].source.alias = e.target.value })} /></label><ColumnSelect label="Join left column" value={j.left} sources={sources} onChange={v => edit(n => { n.joins[i].left = v })} /><ColumnSelect label="Join right column" value={j.right} sources={sources} onChange={v => edit(n => { n.joins[i].right = v })} /><button type="button" onClick={() => edit(n => n.joins.splice(i, 1))}>Remove join</button></fieldset>)}
        <button type="button" disabled={!tables[0]?.columns.length || draft.joins.length >= 8} onClick={() => edit(n => { const alias = `j${n.joins.length + 1}`; n.joins.push({ type: 'left', source: { tableId: tables[0].id, alias }, left: ref(n.source.alias, sources[0].table?.columns[0]?.id || ''), right: ref(alias, tables[0].columns[0].id) }) })}>Add join</button>
        {draft.projections.map((p, i) => <fieldset key={i}><legend>Projection {i + 1}</legend><label>Output name<input value={p.name} onChange={e => edit(n => { n.projections[i].name = e.target.value })} /></label><label>Projection expression<select value={p.expression.kind === 'column' ? 'column' : p.expression.function} onChange={e => edit(n => { const old = n.projections[i].expression, argument = old.kind === 'column' ? old : old.argument; n.projections[i].expression = e.target.value === 'column' ? argument || ref(n.source.alias, sources[0].table?.columns[0]?.id || '') : { kind: 'aggregate', function: e.target.value, argument } })}>{['column', 'count', 'sum', 'avg', 'min', 'max'].map(f => <option key={f}>{f}</option>)}</select></label><ColumnSelect label="Projected column" value={p.expression.kind === 'column' ? p.expression : p.expression.argument} sources={sources} onChange={v => edit(n => { if (n.projections[i].expression.kind === 'column') n.projections[i].expression = v; else n.projections[i].expression.argument = v })} />{p.expression.kind === 'aggregate' && p.expression.function === 'count' && <button type="button" onClick={() => edit(n => { n.projections[i].expression.argument = null })}>Use COUNT(*)</button>}<button type="button" onClick={() => edit(n => n.projections.splice(i, 1))}>Remove projection</button></fieldset>)}
        <button type="button" disabled={draft.projections.length >= 100} onClick={() => edit(n => n.projections.push({ name: `output_${n.projections.length + 1}`, expression: ref(n.source.alias, sources[0].table?.columns[0]?.id || '') }))}>Add projection</button>
        <button type="button" onClick={() => edit(n => { n.groupBy = n.projections.filter(p => p.expression.kind === 'column').map(p => p.expression).filter((r, i, a) => a.findIndex(v => text(v) === text(r)) === i) })}>Group all projected columns</button>
        <button type="button" onClick={() => edit(n => { n.groupBy = [] })}>Clear grouping</button>
        <pre aria-label="Grouping columns">{text(draft.groupBy)}</pre>
        <label>Filter operator<select value={draft.where?.op || 'none'} onChange={e => edit(n => { const op = e.target.value, column = ref(n.source.alias, sources[0].table?.columns[0]?.id || ''); n.where = op === 'none' ? null : ['isNull', 'isNotNull'].includes(op) ? { op, column } : { op, left: column, right: { kind: 'literal', value: 0 } } })}><option value="none">No filter</option>{['eq', 'ne', 'lt', 'le', 'gt', 'ge', 'isNull', 'isNotNull'].map(op => <option key={op}>{op}</option>)}{['and', 'or', 'not'].includes(draft.where?.op) && <option value={draft.where.op}>Advanced {draft.where.op}</option>}</select></label>
        {draft.where && !['and', 'or', 'not'].includes(draft.where.op) && <><ColumnSelect label="Filter column" value={draft.where.column || draft.where.left} sources={sources} onChange={v => edit(n => { if (n.where.column) n.where.column = v; else n.where.left = v })} />{draft.where.right?.kind === 'literal' && <><label>Filter literal (JSON primitive)<input value={literalText ?? text(draft.where.right.value)} maxLength={4096} onChange={e => { setLiteralText(e.target.value); invalidate('Filter literal is unapplied. Apply or discard it before generation.') }} /></label><button type="button" onClick={() => { try { const value = JSON.parse(literalText ?? text(draft.where.right.value)); if (value !== null && !['string', 'number', 'boolean'].includes(typeof value)) throw new Error('primitive'); edit(n => { n.where.right.value = value }); setLiteralText(null) } catch { setStatus('Filter literal is invalid. Enter a JSON string, number, boolean or null; typed validation runs on the server.') } }}>Apply filter literal</button></>}</>}
        {literalText !== null && <p>Unapplied filter literal is preserved. <button type="button" onClick={() => { setLiteralText(null); invalidate('Unapplied filter literal explicitly discarded.') }}>Discard filter literal</button></p>}
        <p>Use full JSON for nested AND/OR/NOT or column comparisons. Equality to null is invalid; select isNull/isNotNull. Every nonaggregate projection must be grouped in an aggregate query.</p>
      </>}
      {module === 'evaluator' && <>
        <label>Rule set<select value={draft.rulesetVersion} onChange={e => edit(n => { n.rulesetVersion = e.target.value })}><option value="schema-rules-0.3.0">schema-rules-0.3.0</option></select></label><label>Naming convention<select value={draft.namingConvention} onChange={e => edit(n => { n.namingConvention = e.target.value })}><option value="snake_case">snake_case (advisory)</option><option value="none">No naming advice</option></select></label>
        {tables.map((t, i) => <fieldset key={i}><legend>Diagnostic table {i + 1}</legend><label>Table name<input value={t.name} onChange={e => edit(n => { n.schema.tables[i].name = e.target.value })} /></label><label>Primary key column IDs (comma-separated)<input value={t.primaryKey.join(',')} onChange={e => edit(n => { n.schema.tables[i].primaryKey = e.target.value.split(',').map(v => v.trim()).filter(Boolean) })} /></label>{t.columns.map((c, k) => <div className={styles.controls} key={k}><label>{c.name}: logical type<input value={c.type} onChange={e => edit(n => { n.schema.tables[i].columns[k].type = e.target.value })} /></label><label><span><input type="checkbox" checked={c.nullable} onChange={e => edit(n => { n.schema.tables[i].columns[k].nullable = e.target.checked })} /> {c.name}: nullable</span></label></div>)}</fieldset>)}
        <p>Unique and business-key metadata, relationships, defaults and indexes are editable in JSON. Unknown types and invalid relationships are diagnostic input, not executable SQL. No quality score or performance promise is produced.</p>
      </>}
    </fieldset>
    <label>Full {module} specification JSON<textarea ref={editor} value={json} maxLength={1048576} rows={16} spellCheck={false} onChange={e => { invalidate('JSON changes are unapplied. Guided controls are locked until apply or explicit discard.'); setJson(e.target.value); setUnapplied(true) }} /></label>
    <div className={styles.actions}><button type="button" onClick={apply}>Apply {module} JSON</button><button type="button" disabled={!unapplied} onClick={() => { setJson(text(draft)); setUnapplied(false); invalidate('Unapplied edits explicitly discarded.') }}>Discard unapplied {module} JSON</button><button type="button" disabled={busy || literalText !== null || !capability?.canGenerate} onClick={generate}>{module === 'evaluator' ? 'Evaluate schema' : `Generate ${module}`}</button><button type="button" disabled={busy || !result || !capability?.canExport} onClick={download}>{module === 'evaluator' ? 'Download evaluation report' : `Download ${module} ZIP`}</button></div>
    <p role="status" aria-label={`${module} status`} aria-live="polite" aria-atomic="true">{status}</p>
    {details?.changes && <section aria-label="Migration change summary"><h3>Added / Changed / Removed / Manual</h3>{details.noOp && <p>No-op: snapshots have no changes.</p>}<ul>{details.changes.map((c, i) => <li key={i}>{c.category}: {c.code} <button type="button" onClick={() => navigate(c.path)}>{c.path}</button></li>)}</ul></section>}
    <section aria-label={`${module} findings`}><h3>Findings</h3><label>Finding severity<select value={severity} onChange={e => setSeverity(e.target.value)}><option value="all">All</option><option value="error">Error</option><option value="warning">Warning</option><option value="info">Info</option></select></label>{(details?.truncated || result?.truncated) && <p>Findings truncated at 100.</p>}<ol>{findings.filter(f => severity === 'all' || f.severity === severity).map((f, i) => <li key={i}><strong>{f.severity}: {f.ruleId}</strong> <button type="button" onClick={() => navigate(f.path)}>{f.path}</button><p>{f.message} {f.suggestion}</p>{f.blocks && <p>Blocks: {f.blocks.join(', ') || 'advisory only'}</p>}</li>)}</ol>{details?.checksNotRun?.length > 0 && <><h4>Checks not run</h4><ul>{details.checksNotRun.map((f, i) => <li key={i}>{f.ruleId} at {f.path}: {f.reasonCode}</li>)}</ul></>}</section>
    {result && <section aria-label={`${module} files`}><label>Artifact file<select value={file} onChange={e => setFile(e.target.value)}>{result.files.map(f => <option key={f.path}>{f.path}</option>)}</select></label><textarea aria-label={`${module} artifact preview`} readOnly rows={18} value={activeFile?.content || ''} /><button type="button" onClick={async () => { const ticket = guard.current.start(); try { await navigator.clipboard.writeText(activeFile?.content || ''); if (guard.current.current(ticket)) setStatus('File copied.') } catch { if (guard.current.current(ticket)) setStatus('Clipboard unavailable. Select text or download instead.') } }}>Copy artifact</button></section>}
  </section>
}
