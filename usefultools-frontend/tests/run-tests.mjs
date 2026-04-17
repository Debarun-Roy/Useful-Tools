import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

import { normalizeApiBase, resolveApiBase } from '../src/api/apiBase.js'

async function run(name, fn) {
  try {
    await awaitMaybe(fn())
    console.log(`PASS ${name}`)
  } catch (error) {
    console.error(`FAIL ${name}`)
    console.error(error)
    process.exitCode = 1
  }
}

function awaitMaybe(value) {
  if (value && typeof value.then === 'function') {
    return value
  }
  return Promise.resolve(value)
}

await run('resolveApiBase defaults to same-origin api path', () => {
  assert.equal(resolveApiBase(undefined), '/api')
  assert.equal(resolveApiBase(''), '/api')
})

await run('normalizeApiBase trims and removes trailing slashes', () => {
  assert.equal(
    normalizeApiBase(' https://useful-tools-production-2d71.up.railway.app/api/ '),
    'https://useful-tools-production-2d71.up.railway.app/api'
  )
  assert.equal(normalizeApiBase('/api///'), '/api')
})

await run('cross-origin api base is rejected in production-like browser context', () => {
  globalThis.window = {
    location: {
      origin: 'https://usefultools-deba.vercel.app',
    },
  }

  assert.equal(
    resolveApiBase('https://useful-tools-production-2d71.up.railway.app/api'),
    '/api'
  )

  delete globalThis.window
})

await run('local development may still use an absolute backend base', () => {
  globalThis.window = {
    location: {
      origin: 'http://localhost:3000',
    },
  }

  assert.equal(
    resolveApiBase('http://localhost:8080/UsefulTools/api'),
    'http://localhost:8080/UsefulTools/api'
  )

  delete globalThis.window
})

await run('vercel.json keeps api proxy ahead of spa fallback', async () => {
  const raw = await readFile(new URL('../vercel.json', import.meta.url), 'utf8')
  const config = JSON.parse(raw)

  assert.ok(Array.isArray(config.rewrites))
  assert.deepEqual(config.rewrites[0], {
    source: '/api/:path*',
    destination: 'https://useful-tools-production-2d71.up.railway.app/api/:path*',
  })
  assert.deepEqual(config.rewrites[1], {
    source: '/:path*',
    destination: '/index.html',
  })
})
