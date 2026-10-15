import assert from 'node:assert/strict'
import { parseQuery, queryString, catalogParams } from '../src/pages/EmoteUtilsPage/query.js'
export function checkEmoteQuery() {
  assert.deepEqual(parseQuery(''), { type: 'emoji', category: 'Smileys & Emotion', subgroup: '', q: '', includeAlternates: false, offset: 0 })
  for (const q of [':)', ':))', '%', '_', '\\', '😀', '🇮🇳', '  a  ', '<script>']) {
    const query = { ...parseQuery(''), q, offset: 60 }
    assert.deepEqual(parseQuery(queryString(query)), query)
    assert.equal(new URLSearchParams(catalogParams(query, 'v')).get('q'), q)
  }
  for (const invalid of ['-1', '1.5', 'NaN', '5370', '9999999999999999999']) assert.equal(parseQuery(`offset=${invalid}`).offset, 0)
  assert.equal([...parseQuery('q='+encodeURIComponent('😀'.repeat(101))).q].length,100)
  assert.equal(parseQuery('type=bad&includeAlternates=TRUE&subgroup=bad&category=all').subgroup,'')
  assert.equal(queryString(parseQuery('type=ascii&type=emoji&unknown=x')), 'type=ascii&category=all')
}
