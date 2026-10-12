export function setPrimaryKey(table, columnId, checked) {
  table.primaryKey = checked ? [...new Set([...table.primaryKey, columnId])] : table.primaryKey.filter(id => id !== columnId)
  if (checked) {
    const column = table.columns.find(c => c.id === columnId)
    column.nullable = false
    if (column.default?.kind === 'literal' && column.default.value === null) delete column.default
  }
  // A duplicate constraint on the same ordered key is rejected by the generator.
  table.unique = table.unique.filter(key => JSON.stringify(key) !== JSON.stringify(table.primaryKey))
}

export function setColumnUnique(table, columnId, checked) {
  table.unique = table.unique.filter(key => !(key.length === 1 && key[0] === columnId))
  if (checked) table.unique.push([columnId])
}

export function initialDefault(type) {
  if (['integer', 'bigint', 'decimal'].includes(type)) return 0
  if (type === 'boolean') return false
  if (type === 'json') return '{}'
  return ''
}

export function numericDefault(value) {
  const number = Number(value)
  // Do not silently round large integers/high precision decimals in guided UI.
  // Preserving the string makes the server reject it instead of generating rounded SQL.
  return value !== '' && Number.isFinite(number) && Math.abs(number) <= Number.MAX_SAFE_INTEGER
    && (value.replace(/[^0-9]/g, '').length <= 15 || /^[+-]?\d+$/.test(value) && Number.isSafeInteger(number) && BigInt(value) === BigInt(number)) ? number : value
}

export function editableConstraints(table) {
  const ids = value => Array.isArray(value) && value.every(id => typeof id === 'string')
  return ids(table.primaryKey) && Array.isArray(table.unique) && table.unique.every(ids)
    && table.columns.every(c => typeof c.nullable === 'boolean' && (!Object.hasOwn(c, 'default') ||
      c.default && typeof c.default === 'object' && !Array.isArray(c.default) && typeof c.default.kind === 'string'
      && (c.default.kind !== 'literal' || Object.hasOwn(c.default, 'value') &&
        (c.default.value === null || ['string', 'number', 'boolean'].includes(typeof c.default.value)))))
    && Array.isArray(table.foreignKeys) && table.foreignKeys.every(f => f && typeof f.tableId === 'string'
      && ids(f.columns) && ids(f.targetColumns) && typeof f.onDelete === 'string' && typeof f.onUpdate === 'string')
}

export function newRelationship(table, tables) {
  const parent = tables.find(t => t.id !== table.id && t.primaryKey.length) || tables.find(t => t.id !== table.id) || tables.find(t => t.id === table.id)
  if (!parent) return null
  const targets = parent.primaryKey.length ? parent.primaryKey : parent.columns.slice(0, 1).map(c => c.id)
  const used = new Set()
  const columns = targets.map(id => {
    const type = parent.columns.find(c => c.id === id)?.type
    const column = table.columns.find(c => !used.has(c.id) && !table.primaryKey.includes(c.id) && c.type === type)
    if (column) used.add(column.id)
    return column?.id || ''
  })
  return { columns, tableId: parent.id, targetColumns: [...targets], onDelete: 'NO ACTION', onUpdate: 'NO ACTION' }
}
