import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Vite configuration for UsefulTools frontend.
 *
 * THE PROXY — why it exists:
 * During development, Vite runs on port 3000 and Tomcat runs on port 8080.
 * A fetch('/api/auth/login') from React would try to hit localhost:3000/api/...
 * which doesn't exist. The proxy intercepts any request whose path starts with
 * /api and silently forwards it to localhost:8080. The browser never knows —
 * it thinks it's talking to port 3000 the whole time.
 *
 * This means:
 *   1. No CORS issues during development (same-origin from the browser's view).
 *   2. Session cookies work reliably.
 *   3. All fetch calls use short paths like '/api/auth/login' — no hardcoded
 *      port numbers anywhere in the React code.
 *
 * In production, this proxy is not needed because Tomcat serves both the
 * React build files and the Java API from the same port 8080.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080/UsefulTools',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
