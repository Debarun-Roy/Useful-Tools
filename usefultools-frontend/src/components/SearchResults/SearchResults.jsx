import { useEffect, useState } from 'react'
import { useSearch } from '../../hooks/useSearch'
import styles from './SearchResults.module.css'

/**
 * SearchResults — Component for displaying tool search results.
 *
 * ── Features ─────────────────────────────────────────────────────────────
 * - Search input with debouncing
 * - Result list with relevance indicators
 * - Click handling for tool selection
 * - Loading and error states
 * - Empty state messaging
 * - "No results" messaging
 *
 * ── Props ────────────────────────────────────────────────────────────────
 * @param {string} username           — Current username (optional)
 * @param {Function} onSelectTool     — Callback when tool is selected
 * @param {boolean} compact           — Compact display mode (default: false)
 * @param {number} maxResults         — Maximum results to show (default: 10)
 * @param {string} placeholder        — Search input placeholder text
 *
 * ── Usage ────────────────────────────────────────────────────────────────
 *   <SearchResults
 *     username={user.username}
 *     onSelectTool={(tool) => navigate(tool.path)}
 *     compact={false}
 *     maxResults={10}
 *     placeholder="Search tools..."
 *   />
 */
export default function SearchResults({
  username = null,
  onSelectTool = null,
  compact = false,
  maxResults = 10,
  placeholder = 'Search tools...',
}) {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const { search, searchResults, loading, error } = useSearch(username)

  // Debounce search queries — wait 300ms after user stops typing
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query)
    }, 300)

    return () => clearTimeout(timer)
  }, [query])

  // Execute search when debounced query changes
  useEffect(() => {
    if (debouncedQuery.trim().length > 0) {
      search(debouncedQuery)
    }
  }, [debouncedQuery, search])

  // Limit results to maxResults
  const displayedResults = searchResults.slice(0, maxResults)

  const handleClear = () => {
    setQuery('')
    setDebouncedQuery('')
  }

  const handleSelectTool = (tool) => {
    if (onSelectTool) {
      onSelectTool(tool)
    }
    handleClear()
  }

  return (
    <div className={`${styles.container} ${compact ? styles.compact : ''}`}>
      {/* Search Input */}
      <div className={styles.searchBox}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder={placeholder}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search tools"
        />
        {query && (
          <button
            className={styles.clearBtn}
            onClick={handleClear}
            aria-label="Clear search"
            title="Clear search"
          >
            ✕
          </button>
        )}
      </div>

      {/* Results Container */}
      {query.trim().length > 0 && (
        <div className={styles.resultsContainer}>
          {/* Loading State */}
          {loading && (
            <div className={styles.loadingState}>
              <span className={styles.spinner}>⟳</span>
              <span>Searching...</span>
            </div>
          )}

          {/* Error State */}
          {error && !loading && (
            <div className={styles.errorState} role="alert">
              <span className={styles.errorIcon}>⚠</span>
              <span>{error}</span>
            </div>
          )}

          {/* Results */}
          {!loading && !error && displayedResults.length > 0 && (
            <ul className={styles.resultsList}>
              {displayedResults.map((tool, index) => (
                <li key={index} className={styles.resultItem}>
                  <button
                    className={styles.resultButton}
                    onClick={() => handleSelectTool(tool)}
                    aria-label={`Select ${tool.name}`}
                  >
                    <span className={styles.resultIcon}>{tool.icon}</span>
                    <span className={styles.resultContent}>
                      <span className={styles.resultName}>{tool.name}</span>
                      <span className={styles.resultDesc}>{tool.description}</span>
                    </span>
                    {tool.relevance !== undefined && (
                      <span
                        className={`${styles.relevanceBadge} ${
                          tool.relevance >= 50
                            ? styles.relevanceHigh
                            : tool.relevance >= 20
                              ? styles.relevanceMedium
                              : styles.relevanceLow
                        }`}
                        title={`Relevance: ${tool.relevance}%`}
                      >
                        {Math.round(tool.relevance / 20)}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}

          {/* No Results */}
          {!loading && !error && displayedResults.length === 0 && query.trim().length > 0 && (
            <div className={styles.noResults}>
              <span className={styles.noResultsIcon}>🔍</span>
              <p className={styles.noResultsText}>No tools found for "{query}"</p>
              <p className={styles.noResultsHint}>Try a different search term</p>
            </div>
          )}
        </div>
      )}

      {/* Helper Text */}
      {query.trim().length === 0 && (
        <div className={styles.helperText}>Type to search for tools or features</div>
      )}
    </div>
  )
}
