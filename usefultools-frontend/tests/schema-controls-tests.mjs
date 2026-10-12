import test from 'node:test'
import assert from 'node:assert/strict'
import { setPrimaryKey, setColumnUnique, initialDefault, newRelationship, numericDefault, editableConstraints } from '../src/pages/BackendSupportPage/schemaControls.mjs'

const table = () => ({ id: 'orders', primaryKey: [], unique: [['id'], ['id', 'name']], columns: [
  { id: 'id', type: 'integer', nullable: true, default: { kind: 'literal', value: null } },
  { id: 'customer_id', type: 'integer', nullable: false },
], foreignKeys: [] })
test('primary key selection clears nullable and NULL default without a duplicate UNIQUE', () => {
  const t = table(); setPrimaryKey(t, 'id', true); setPrimaryKey(t, 'id', true)
  assert.deepEqual(t.primaryKey, ['id']); assert.equal(t.columns[0].nullable, false)
  assert.equal(t.columns[0].default, undefined); assert.deepEqual(t.unique, [['id', 'name']])
  setPrimaryKey(t, 'id', false); assert.equal(t.columns[0].nullable, false)
})
test('unique toggle preserves composite constraints and is idempotent', () => {
  const t = table(); setColumnUnique(t, 'customer_id', true); setColumnUnique(t, 'customer_id', true)
  assert.equal(t.unique.length, 3); setColumnUnique(t, 'customer_id', false)
  assert.deepEqual(t.unique, [['id'], ['id', 'name']])
})
test('default initialization retains false, zero and empty string as typed values', () => {
  assert.equal(initialDefault('boolean'), false); assert.equal(initialDefault('integer'), 0)
  assert.equal(initialDefault('text'), ''); assert.equal(initialDefault('json'), '{}')
})
test('child relationship references the parent key using a non-PK child column', () => {
  const child = table(); child.primaryKey = ['id']
  const parent = { id: 'customers', primaryKey: ['id'], columns: [{ id: 'id', type: 'integer' }] }
  assert.deepEqual(newRelationship(child, [child, parent]), { columns: ['customer_id'], tableId: 'customers', targetColumns: ['id'], onDelete: 'NO ACTION', onUpdate: 'NO ACTION' })
  assert.equal(newRelationship(child, [child]).tableId, child.id)
})
test('numeric defaults do not silently round unsafe integers or high precision decimals', () => {
  assert.equal(numericDefault('0'), 0); assert.equal(numericDefault('12.5'), 12.5)
  assert.equal(numericDefault('9007199254740993'), '9007199254740993')
  assert.equal(numericDefault('9007199254740991.1'), '9007199254740991.1')
  assert.equal(numericDefault(''), '')
})
test('malformed constraint JSON cannot crash guided forms', () => {
  const t = table(); assert(editableConstraints(t)); t.unique = [null]; assert(!editableConstraints(t))
  t.unique = []; t.foreignKeys = [{}]; assert(!editableConstraints(t))
})

test('composite primary key enforces nullability for every selected member', () => {
  const t = table(); setPrimaryKey(t, 'id', true); setPrimaryKey(t, 'customer_id', true)
  assert.deepEqual(t.primaryKey, ['id', 'customer_id']); assert(t.columns.every(c => !c.nullable))
  setPrimaryKey(t, 'id', false); assert.deepEqual(t.primaryKey, ['customer_id'])
})
test('malformed default objects are preserved in JSON instead of crashing forms', () => {
  for (const value of [null, [], {}, {kind:'literal'}, {kind:'literal', value:{}}]) {
    const t = table(); t.columns[0].default = value; assert.equal(editableConstraints(t), false)
  }
  const t=table(); t.columns[0].default={kind:'expression', value:'unsafe()'}; assert(editableConstraints(t))
})
test('relationships preserve ordered composite mapping and do not mutate parent or child', () => {
  const t=table(); t.columns.push({id:'second',type:'text',nullable:false})
  const p={id:'parent',primaryKey:['a','b'],columns:[{id:'a',type:'integer'},{id:'b',type:'text'}]}
  const before=structuredClone([t,p]); const fk=newRelationship(t,[t,p])
  assert.deepEqual(fk.targetColumns,['a','b']); assert.deepEqual(fk.columns,['id','second'])
  assert.deepEqual([t,p],before)
})
