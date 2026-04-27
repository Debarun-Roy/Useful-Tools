import { useCallback, useState } from 'react'
import { request } from '../api/apiClient'

/**
 * useSearch — Hook for tool discovery and search functionality.
 *
 * ── Purpose ──────────────────────────────────────────────────────────────
 * Provides stateful search and recommendation features for discovering tools.
 *
 * ── Features ─────────────────────────────────────────────────────────────
 * - Fuzzy search tools by name/description
 * - Get personalized tool recommendations based on usage
 * - Record tool usage for future recommendations
 * - Debounced search requests to avoid excessive API calls
 *
 * ── API Endpoints ────────────────────────────────────────────────────────
 * GET  /api/search/tools              - Search tools
 * GET  /api/search/recommendations    - Get recommendations
 * POST /api/search/record-usage       - Record tool usage
 *
 * ── Usage ────────────────────────────────────────────────────────────────
 *   const { search, searchResults, recommendations, recordUsage, loading } = useSearch(username)
 *   await search('calculator')
 *   await recommendations(5)
 *   await recordUsage('/calculator')
 */
export function useSearch(username) {
  const [searchResults, setSearchResults] = useState([])
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  /**
   * Searches for tools by query string.
   *
   * @param query Search query (minimum 1 character)
   * @returns Promise<Array> Array of matching tools sorted by relevance
   */
  const search = useCallback(
    async (query) => {
      if (!query || query.trim().length === 0) {
        setSearchResults([])
        return []
      }

      setLoading(true)
      setError(null)

      try {
        const params = new URLSearchParams({ q: query.trim() })
        const { status, data: json } = await request(`/search/tools?${params.toString()}`)

        if (status !== 200) {
          throw new Error(json.error || 'Search failed')
        }

        const results = json.data?.results || []
        setSearchResults(results)
        return results
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Search error'
        setError(errorMessage)
        setSearchResults([])
        return []
      } finally {
        setLoading(false)
      }
    },
    []
  )

  /**
   * Gets personalized tool recommendations.
   *
   * @param limit Maximum number of recommendations (default: 5, max: 20)
   * @returns Promise<Array> Array of recommended tools
   */
  const getRecommendations = useCallback(
    async (limit = 5) => {
      setLoading(true)
      setError(null)

      try {
        const params = new URLSearchParams()
        if (username) params.append('username', username)
        if (limit) params.append('limit', Math.min(Math.max(limit, 1), 20))

        const { status, data: json } = await request(`/search/recommendations?${params.toString()}`)

        if (status !== 200) {
          throw new Error(json.error || 'Failed to fetch recommendations')
        }

        const recs = json.data?.recommendations || []
        setRecommendations(recs)
        return recs
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Recommendation error'
        setError(errorMessage)
        setRecommendations([])
        return []
      } finally {
        setLoading(false)
      }
    },
    [username]
  )

  /**
   * Records usage of a tool for recommendation tracking.
   *
   * @param toolPath Tool path (e.g., "/calculator")
   * @param toolName Optional tool display name
   * @returns Promise<Object> Server response
   */
  const recordUsage = useCallback(
    async (toolPath, toolName = null) => {
      if (!toolPath) {
        return null
      }

      try {
        const { status, data: json } = await request('/search/record-usage', {
          method: 'POST',
          isJson: true,
          body: { toolPath, toolName: toolName || toolPath },
        })

        if (status !== 200) {
          console.warn('Failed to record usage:', json.error)
          return null
        }

        return json.data
      } catch (err) {
        console.warn('Failed to record usage:', err)
        return null
      }
    },
    []
  )

  return {
    search,
    searchResults,
    recommendations,
    getRecommendations,
    recordUsage,
    loading,
    error,
  }
}
