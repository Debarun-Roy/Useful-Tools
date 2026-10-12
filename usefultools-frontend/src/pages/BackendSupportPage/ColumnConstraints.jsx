import { initialDefault, numericDefault, setColumnUnique, setPrimaryKey } from './schemaControls.mjs'

export default function ColumnConstraints({ table, column, onEdit }) {
  const primary = table.primaryKey.includes(column.id)
  const solePrimary = primary && table.primaryKey.length === 1
  const unique = table.unique.some(key => key.length === 1 && key[0] === column.id)
  const mode = !column.default ? 'none' : column.default.kind === 'literal'
    ? column.default.value === null ? 'null' : 'literal' : column.default.kind
  const numeric = ['integer', 'bigint', 'decimal'].includes(column.type)
  const changeColumn = update => onEdit(t => update(t.columns.find(c => c.id === column.id)))
  return <>
    <label><input type="checkbox" checked={column.nullable} disabled={primary}
      onChange={e => changeColumn(c => { c.nullable = e.target.checked; if (!c.nullable && c.default?.value === null) delete c.default })} /> Nullable</label>
    <label><input type="checkbox" checked={primary} onChange={e => onEdit(t => setPrimaryKey(t, column.id, e.target.checked))} /> Primary key (selection order)</label>
    <label><input type="checkbox" checked={solePrimary || unique} disabled={solePrimary}
      onChange={e => onEdit(t => setColumnUnique(t, column.id, e.target.checked))} /> Unique</label>
    {primary && <p>Primary keys cannot be NULL. A single-column primary key is already unique.</p>}
    <label>Default value kind<select aria-label="Default value kind" value={mode} onChange={e => changeColumn(c => {
      const kind = e.target.value
      if (kind === 'none') delete c.default
      else c.default = kind === 'currentTimestamp' ? { kind } : { kind: 'literal', value: kind === 'null' ? null : initialDefault(c.type) }
    })}>
      <option value="none">No default</option><option value="literal">Literal value</option>
      <option value="null" disabled={!column.nullable || primary}>NULL</option>
      <option value="currentTimestamp" disabled={column.type !== 'timestamp'}>Current timestamp</option>
      {!['none', 'literal', 'null', 'currentTimestamp'].includes(mode) && <option value={mode}>Diagnostic expression (edit in JSON)</option>}
    </select></label>
    {mode === 'literal' && (column.type === 'boolean' && typeof column.default.value === 'boolean'
      ? <label>Default value<select aria-label="Default value" value={String(column.default.value)} onChange={e => changeColumn(c => { c.default.value = e.target.value === 'true' })}><option value="false">false</option><option value="true">true</option></select></label>
      : <label>Default value<input aria-label="Default value" type={numeric ? 'number' : 'text'} step="any" maxLength={4096}
        value={String(column.default.value)} onChange={e => { const value = e.target.value; changeColumn(c => {
          c.default.value = numeric ? numericDefault(value) : value
        }) }} /></label>)}
    {mode === 'literal' && <p>Enter a {column.type} value, without SQL quoting. Dates use YYYY-MM-DD; timestamps use UTC ISO format. JSON defaults contain JSON text. Server validation checks type and range. Type changes preserve existing defaults for review. Numbers beyond safe browser precision are rejected rather than rounded.</p>}
  </>
}
