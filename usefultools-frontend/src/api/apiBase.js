function stripTrailingSlashes(value) {
  return value.replace(/\/+$/, '')
}

export function normalizeApiBase(value) {
  if (!value || !value.trim()) {
    return '/api'
  }

  return stripTrailingSlashes(value.trim())
}

export function resolveApiBase(envValue) {
  return normalizeApiBase(envValue)
}
