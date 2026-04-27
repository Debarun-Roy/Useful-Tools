import { useNotes } from './NotesContext'
import styles from './TextUtilitiesPage.module.css'

/*
 * SaveNoteButton — small "💾 Save to notes" pill rendered next to each
 * subtool's output. Click → saves a note via NotesContext and shows a
 * brief inline confirmation via the context's flash state.
 *
 * Disabling rules: empty content disables the button; the parent decides
 * what counts as content (typically `output || input` for the tool).
 */
export default function SaveNoteButton({ tool, title, content, disabled }) {
  const { saveAndFlash, flash } = useNotes()
  const isEmpty = !content || !content.toString().trim()
  const justSaved = flash && flash.tool === tool

  function handleClick() {
    if (isEmpty || disabled) return
    saveAndFlash(tool, title, content.toString())
  }

  return (
    <button
      type="button"
      className={justSaved ? styles.saveNoteBtnDone : styles.saveNoteBtn}
      onClick={handleClick}
      disabled={disabled || isEmpty}
      title="Save this result to your notes"
    >
      {justSaved ? '✓ Saved' : '💾 Save to notes'}
    </button>
  )
}
