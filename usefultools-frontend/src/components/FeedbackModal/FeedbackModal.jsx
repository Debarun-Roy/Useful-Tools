import { useState } from 'react'
import { useAuth }   from '../../auth/useAuth'
import { submitFeedback } from '../../api/apiClient'
import styles from './FeedbackModal.module.css'

// ── Constants ─────────────────────────────────────────────────────────────────

const FEATURES = [
  'Calculator',
  'Number Analyser',
  'Password Vault',
  'Unit Converter',
  'Text Utilities',
  'Encoding & Decoding',
  'Code Utilities',
  'Web Dev Helpers',
]

const RATING_LABELS = {
  1: 'Poor',
  2: 'Fair',
  3: 'Good',
  4: 'Very Good',
  5: 'Excellent',
}

// ── StarRating (internal) ─────────────────────────────────────────────────────

function StarRating({ value, onChange, size = 'lg', disabled = false, id }) {
  const [hovered, setHovered] = useState(0)

  return (
    <div className={styles.stars} data-size={size}>
      {[1, 2, 3, 4, 5].map(star => (
        <button
          key={star}
          type="button"
          id={id && star === 1 ? id : undefined}
          className={`${styles.star} ${(hovered || value) >= star ? styles.starFilled : ''}`}
          onMouseEnter={() => !disabled && setHovered(star)}
          onMouseLeave={() => !disabled && setHovered(0)}
          onClick={() => !disabled && onChange(star === value ? 0 : star)}
          disabled={disabled}
          aria-label={`${star} star${star !== 1 ? 's' : ''}`}
          aria-pressed={value === star}
        >
          ★
        </button>
      ))}
      {value > 0 && (
        <span className={styles.ratingLabel}>{RATING_LABELS[value]}</span>
      )}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export default function FeedbackModal() {
  const { isLoggedIn } = useAuth()

  const [isOpen,           setIsOpen]           = useState(false)
  const [overallRating,    setOverallRating]     = useState(0)
  const [generalComment,   setGeneralComment]    = useState('')
  const [showFeatureSection, setShowFeatureSection] = useState(false)
  const [selectedFeatures, setSelectedFeatures] = useState([])
  const [featureRatings,   setFeatureRatings]    = useState({})
  const [featureComments,  setFeatureComments]   = useState({})
  const [loading,          setLoading]           = useState(false)
  const [success,          setSuccess]           = useState(false)
  const [error,            setError]             = useState('')

  // ── Only render for authenticated users ──────────────────────────────────────
  if (!isLoggedIn) return null

  // ── Handlers ─────────────────────────────────────────────────────────────────

  function handleFeatureToggle(feature) {
    if (feature === 'All') {
      if (selectedFeatures.length === FEATURES.length) {
        setSelectedFeatures([])
      } else {
        setSelectedFeatures([...FEATURES])
      }
      return
    }
    setSelectedFeatures(prev =>
      prev.includes(feature)
        ? prev.filter(f => f !== feature)
        : [...prev, feature]
    )
  }

  function handleFeatureRating(feature, rating) {
    setFeatureRatings(prev => ({ ...prev, [feature]: rating }))
  }

  function handleFeatureComment(feature, comment) {
    setFeatureComments(prev => ({ ...prev, [feature]: comment }))
  }

  async function handleSubmit() {
    if (overallRating === 0) {
      setError('Please provide an overall rating before submitting.')
      return
    }

    setLoading(true)
    setError('')

    const featureFeedback = selectedFeatures.map(f => ({
      featureName: f,
      rating:  featureRatings[f]  || null,
      comment: featureComments[f] || null,
    }))

    try {
      const { data } = await submitFeedback({
        overallRating,
        generalComment: generalComment.trim() || null,
        featureFeedback,
      })

      if (data.success) {
        setSuccess(true)
        setTimeout(handleClose, 3000)
      } else {
        setError(data.error || 'Failed to submit feedback. Please try again.')
      }
    } catch {
      setError('Could not reach the server. Please try again later.')
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setIsOpen(false)
    // Reset all state after the close animation completes
    setTimeout(() => {
      setOverallRating(0)
      setGeneralComment('')
      setShowFeatureSection(false)
      setSelectedFeatures([])
      setFeatureRatings({})
      setFeatureComments({})
      setSuccess(false)
      setError('')
    }, 200)
  }

  const allSelected = selectedFeatures.length === FEATURES.length

  // ── Render ────────────────────────────────────────────────────────────────────

  return (
    <>
      {/* ── Floating trigger button ────────────────────────────────────── */}
      <button
        className={styles.trigger}
        onClick={() => setIsOpen(true)}
        aria-label="Give feedback"
        title="Share your feedback about UsefulTools"
      >
        <span className={styles.triggerIcon} aria-hidden="true">💬</span>
        <span className={styles.triggerLabel}>Feedback</span>
      </button>

      {/* ── Modal overlay ─────────────────────────────────────────────── */}
      {isOpen && (
        <div
          className={styles.overlay}
          onClick={e => { if (e.target === e.currentTarget) handleClose() }}
          role="presentation"
        >
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="feedback-modal-title"
          >
            {/* Header */}
            <div className={styles.header}>
              <div className={styles.headerContent}>
                <h2 id="feedback-modal-title" className={styles.title}>
                  Share Your Feedback
                </h2>
                <p className={styles.subtitle}>Help us improve UsefulTools</p>
              </div>
              <button
                className={styles.closeBtn}
                onClick={handleClose}
                aria-label="Close feedback form"
                disabled={loading}
              >
                ×
              </button>
            </div>

            {/* ── Success state ──────────────────────────────────────────── */}
            {success ? (
              <div className={styles.successPanel}>
                <span className={styles.successIcon} aria-hidden="true">✓</span>
                <h3 className={styles.successTitle}>Thank you!</h3>
                <p className={styles.successBody}>
                  Your feedback has been recorded. We appreciate you taking the time!
                </p>
              </div>
            ) : (
              /* ── Form ─────────────────────────────────────────────────── */
              <div className={styles.body}>

                {/* Overall rating — required */}
                <div className={styles.section}>
                  <label className={styles.sectionLabel} htmlFor="overall-star-1">
                    Overall experience <span className={styles.required} aria-label="required">*</span>
                  </label>
                  <StarRating
                    id="overall-star-1"
                    value={overallRating}
                    onChange={setOverallRating}
                    size="lg"
                    disabled={loading}
                  />
                </div>

                {/* General comment — optional */}
                <div className={styles.section}>
                  <label className={styles.sectionLabel} htmlFor="general-comment">
                    General comments
                    <span className={styles.optionalTag}>optional</span>
                  </label>
                  <textarea
                    id="general-comment"
                    className={styles.textarea}
                    value={generalComment}
                    onChange={e => setGeneralComment(e.target.value)}
                    placeholder="Share your thoughts about the application overall…"
                    rows={3}
                    disabled={loading}
                    maxLength={1000}
                  />
                  <span className={styles.charCount}>{generalComment.length} / 1000</span>
                </div>

                {/* Feature-specific feedback — optional accordion */}
                <div className={styles.section}>
                  <button
                    type="button"
                    className={styles.accordionToggle}
                    onClick={() => setShowFeatureSection(v => !v)}
                    disabled={loading}
                    aria-expanded={showFeatureSection}
                    aria-controls="feature-section"
                  >
                    <span>Rate specific features</span>
                    <span className={styles.optionalTag}>optional</span>
                    <span className={styles.accordionIcon} aria-hidden="true">
                      {showFeatureSection ? '▲' : '▼'}
                    </span>
                  </button>

                  {showFeatureSection && (
                    <div id="feature-section" className={styles.featureSection}>

                      {/* Feature chip selector */}
                      <div className={styles.featureSelectWrap}>
                        <span className={styles.fieldLabel}>
                          Select the features you want to rate:
                        </span>
                        <div className={styles.chipCloud} role="group" aria-label="Feature selection">
                          {/* "All" shortcut */}
                          <button
                            type="button"
                            className={`${styles.chip} ${allSelected ? styles.chipActive : ''}`}
                            onClick={() => handleFeatureToggle('All')}
                            disabled={loading}
                            aria-pressed={allSelected}
                          >
                            All
                          </button>

                          {FEATURES.map(f => (
                            <button
                              key={f}
                              type="button"
                              className={`${styles.chip} ${selectedFeatures.includes(f) ? styles.chipActive : ''}`}
                              onClick={() => handleFeatureToggle(f)}
                              disabled={loading}
                              aria-pressed={selectedFeatures.includes(f)}
                            >
                              {f}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Per-feature rating + comment forms */}
                      {selectedFeatures.length > 0 && (
                        <div className={styles.featureForms}>
                          {selectedFeatures.map(feature => (
                            <div key={feature} className={styles.featureForm}>
                              <div className={styles.featureFormHeader}>
                                <span className={styles.featureBadge} aria-hidden="true">◈</span>
                                <span className={styles.featureName}>{feature}</span>
                              </div>
                              <StarRating
                                value={featureRatings[feature] || 0}
                                onChange={r => handleFeatureRating(feature, r)}
                                size="sm"
                                disabled={loading}
                              />
                              <textarea
                                className={`${styles.textarea} ${styles.textareaSm}`}
                                value={featureComments[feature] || ''}
                                onChange={e => handleFeatureComment(feature, e.target.value)}
                                placeholder={`Comments about ${feature} (optional)…`}
                                rows={2}
                                disabled={loading}
                                maxLength={500}
                                aria-label={`Comment for ${feature}`}
                              />
                            </div>
                          ))}
                        </div>
                      )}

                      {selectedFeatures.length === 0 && (
                        <p className={styles.featureHint}>
                          Select one or more features above to leave specific feedback for them.
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Error banner */}
                {error && (
                  <div className={styles.errorBanner} role="alert">{error}</div>
                )}

                {/* Action buttons */}
                <div className={styles.actions}>
                  <button
                    type="button"
                    className={styles.cancelBtn}
                    onClick={handleClose}
                    disabled={loading}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className={styles.submitBtn}
                    onClick={handleSubmit}
                    disabled={loading || overallRating === 0}
                    title={overallRating === 0 ? 'Please select an overall rating first' : undefined}
                  >
                    {loading ? 'Submitting…' : 'Submit Feedback'}
                  </button>
                </div>

              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
