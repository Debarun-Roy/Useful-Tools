export const TYPES = { emoji: 'Emoji', ascii: 'Text faces', kaomoji: 'Kaomoji', symbol: 'Symbols', all: 'All expressions' }
export function parseQuery(search) {
  const p = new URLSearchParams(search)
  const type = Object.hasOwn(TYPES, p.get('type')) ? p.get('type') : 'emoji'
  const rawOffset = p.get('offset') || '0'
  const offset = /^\d+$/.test(rawOffset) && Number(rawOffset) <= 5369 ? Math.floor(Number(rawOffset) / 60) * 60 : 0
  const category = p.has('category') ? (p.get('category') === 'all' ? '' : p.get('category')) : type === 'emoji' ? 'Smileys & Emotion' : ''
  return { type, category, subgroup: type === 'emoji' && category ? p.get('subgroup') || '' : '', q: [...(p.get('q') || '')].slice(0, 100).join(''), includeAlternates: p.get('includeAlternates') === 'true', offset }
}
export function queryString(q) {
  const p = new URLSearchParams({ type: q.type, category: q.category || 'all' })
  if (q.subgroup) p.set('subgroup', q.subgroup)
  if (q.q) p.set('q', q.q)
  if (q.includeAlternates) p.set('includeAlternates', 'true')
  if (q.offset) p.set('offset', String(q.offset))
  return p.toString()
}
export function catalogParams(q, version) {
  const p = { type: q.type, includeAlternates: String(q.includeAlternates), limit: '60', offset: String(q.offset), version }
  for (const key of ['category', 'subgroup', 'q']) if (q[key]) p[key] = q[key]
  return p
}
