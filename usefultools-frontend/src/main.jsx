import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

/**
 * main.jsx — the entry point Vite loads first.
 *
 * createRoot mounts the entire React application into the <div id="root">
 * in index.html. Everything you see in the browser is rendered by React
 * from this single mounting point.
 *
 * StrictMode: a development-only tool that helps catch bugs early by
 * intentionally rendering components twice and warning about deprecated
 * patterns. It has no effect in a production build.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
