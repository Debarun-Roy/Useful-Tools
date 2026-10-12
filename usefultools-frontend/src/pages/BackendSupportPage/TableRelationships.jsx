import { newRelationship } from './schemaControls.mjs'

export default function TableRelationships({ table, tables, onEdit }) {
  const child = table.foreignKeys.length > 0
  const children = tables.filter(t => t.foreignKeys.some(f => f.tableId === table.id)).map(t => t.name)
  const add = () => { const relationship = newRelationship(table, tables); if (relationship) onEdit(t => t.foreignKeys.push(relationship)) }
  return <fieldset><legend>Parent and child relationships</legend>
    <p>Foreign keys belong to the child table and reference a parent's primary or unique key. A table may be both a parent and a child. Ordered comma-separated IDs support composite keys. IDs survive database renames; removing referenced columns or tables produces blocking validation findings. Self references are supported; multi-table cycles cannot be generated.</p>
    <label><input type="checkbox" checked={child} onChange={e => {
      if (e.target.checked) add()
      else if (window.confirm('Remove all parent relationships and foreign keys from this child table? Column data will remain.')) onEdit(t => { t.foreignKeys = [] })
    }} /> Child table with parent relationships</label>
    {children.length > 0 && <p>Parent of: {children.join(', ')}</p>}
    {!child && <p>Select the child option to define a foreign key. With one table, this creates a self reference; review the column mapping before generating.</p>}
    {child && <>
      {table.foreignKeys.map((f, i) => <fieldset key={i}><legend>Relationship {i + 1}</legend>
        <label>Child column IDs<input value={f.columns.join(',')} onChange={e => onEdit(t => { t.foreignKeys[i].columns = e.target.value.split(',').map(v => v.trim()) })} /></label>
        <label>Parent table<select value={f.tableId} onChange={e => onEdit(t => {
          const parent = tables.find(p => p.id === e.target.value)
          t.foreignKeys[i].tableId = parent.id; t.foreignKeys[i].targetColumns = [...parent.primaryKey]
        })}>{tables.map(t => <option key={t.id} value={t.id}>{t.name} ({t.id})</option>)}</select></label>
        <label>Parent column IDs<input value={f.targetColumns.join(',')} onChange={e => onEdit(t => { t.foreignKeys[i].targetColumns = e.target.value.split(',').map(v => v.trim()) })} /></label>
        {['onDelete', 'onUpdate'].map(action => <label key={action}>{action}<select value={f[action]} onChange={e => onEdit(t => { t.foreignKeys[i][action] = e.target.value })}>
          {['NO ACTION', 'RESTRICT', 'CASCADE', 'SET NULL'].map(value => <option key={value}>{value}</option>)}
        </select></label>)}
        <button type="button" onClick={() => { if (window.confirm('Remove this foreign key relationship? Column data will remain.')) onEdit(t => { t.foreignKeys.splice(i, 1) }) }}>Remove relationship {i + 1}</button>
      </fieldset>)}
      <button type="button" onClick={add}>Add parent relationship</button>
    </>}
  </fieldset>
}
