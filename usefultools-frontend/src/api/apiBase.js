function stripTrailingSlashes(value) {
  return value.replace(/\/+$/, '')
}

function getWindowOrigin() {
  if (typeof window === 'undefined' || !window.location?.origin) {
    return null
  }

  return window.location.origin
}

function isLocalOrigin(origin) {
  return origin === 'http://localhost:3000'
    || origin === 'http://127.0.0.1:3000'
    || origin === 'http://localhost:5173'
    || origin === 'http://127.0.0.1:5173'
}

export function normalizeApiBase(value) {
  if (!value || !value.trim()) {
    return '/api'
  }

  const normalized = stripTrailingSlashes(value.trim())

  if (!/^https?:\/\//i.test(normalized)) {
    return normalized
  }

  const currentOrigin = getWindowOrigin()
  if (!currentOrigin) {
    return normalized
  }

  let apiOrigin
  try {
    apiOrigin = new URL(normalized).origin
  } catch {
    return '/api'
  }

  if (apiOrigin === currentOrigin || isLocalOrigin(currentOrigin)) {
    return normalized
  }

  console.warn(
    `Ignoring cross-origin VITE_API_BASE "${normalized}" and using same-origin "/api" instead.`
  )
  return '/api'
}

export function resolveApiBase(envValue) {
  return normalizeApiBase(envValue)
}
