import { useState } from 'react'
import { NotesContext as Ctx } from './useNotes'
import useTextUtilNotes from '../../hooks/useTextUtilNotes'

/*
 * NotesContext — page-scoped wrapper around useTextUtilNotes so every subtool
 * (WordCounter, CaseConverter, TextDiff, RegexTester, SlugGenerator, LoremIpsum)
 * reads and writes the same per-user notes store without prop drilling.
 *
 * Also tracks a "flash" message id so the active subtool can show a brief
 * "Saved" confirmation next to its Save button without each component
 * inventing its own toast plumbing.
 */

export function NotesProvider({ username, children }) {
  const store = useTextUtilNotes(username)
  const [flash, setFlash] = useState(null) // { tool, at }

  function saveAndFlash(tool, title, content) {
    const note = store.addNote(tool, title, content)
    if (note) {
      setFlash({ tool, at: Date.now() })
      setTimeout(() => {
        setFlash(prev => (prev && prev.tool === tool ? null : prev))
      }, 1800)
    }
    return note
  }

  return (
    <Ctx.Provider value={{ ...store, saveAndFlash, flash }}>
      {children}
    </Ctx.Provider>
  )
}
