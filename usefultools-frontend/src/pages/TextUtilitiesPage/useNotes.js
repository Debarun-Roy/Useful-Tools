import { createContext, useContext } from 'react'

export const NotesContext = createContext(null)

export function useNotes() {
  const ctx = useContext(NotesContext)
  if (!ctx) throw new Error('useNotes must be inside <NotesProvider>')
  return ctx
}
