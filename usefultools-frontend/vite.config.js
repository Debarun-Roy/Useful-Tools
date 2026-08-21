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
 *
 * SPRINT 24 — Code splitting:
 * manualChunks groups the 22 lazy page chunks into logical buckets so the
 * browser can cache related tools together. Each group is fetched once and
 * re-used on subsequent navigations to tools in the same group.
 *
 * Chunk groups:
 *   vendor        — React, React-DOM, React-Router (largest, changes least often)
 *   dashboard     — DashboardPage + ToolRecommendations (loaded on every login)
 *   calculator    — All calculator sub-pages
 *   tools-data    — Data-oriented tools (Analyser, Unit Converter)
 *   tools-text    — Text/Encoding/Code/Markdown tools
 *   tools-web     — WebDev, Image, Dev Utilities, Time Utilities
 *   tools-sprint  — All Sprint 17–23 tools (Formatter, Regex, DataViz,
 *                   Color, Placeholder, SQL, Bulk Converter)
 *   vault         — Password Vault (kept separate — sensitive, rarely visited)
 *   admin         — Admin panel (separate — only admin users ever load this)
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
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          // vendor — React ecosystem (cached independently, changes least)
          if (id.includes('node_modules/react') ||
              id.includes('node_modules/react-dom') ||
              id.includes('node_modules/react-router')) {
            return 'vendor'
          }
          // Third-party libs bundled separately
          if (id.includes('node_modules/')) {
            return 'vendor-libs'
          }

          const src = id.replace(/\\/g, '/')

          // Admin — only loaded by admin users
          if (src.includes('/pages/AdminPage/')) return 'admin'

          // Password Vault — sensitive, separate cache entry
          if (src.includes('/pages/PasswordVaultPage/')) return 'vault'

          // Dashboard — loaded on every login after auth
          if (src.includes('/pages/DashboardPage/') ||
              src.includes('/components/ToolRecommendations/') ||
              src.includes('/components/RecentActivity/') ||
              src.includes('/components/SearchResults/')) {
            return 'dashboard'
          }

          // Calculator family
          if (src.includes('/pages/CalculatorPage/')) return 'calculator'

          // Sprint 17–23 tools — newest, bundled together
          if (src.includes('/pages/ResponseFormatterPage/') ||
              src.includes('/pages/RegexBuilderPage/') ||
              src.includes('/pages/DataVisualizationPage/') ||
              src.includes('/pages/MarkdownConverterPage/') ||
              src.includes('/pages/ColorToolsPage/') ||
              src.includes('/pages/PlaceholderGeneratorPage/') ||
              src.includes('/pages/SQLFormatterPage/') ||
              src.includes('/pages/BulkConverterPage/')) {
            return 'tools-sprint'
          }

          // Text-oriented legacy tools
          if (src.includes('/pages/TextUtilitiesPage/') ||
              src.includes('/pages/EncodingDecodingPage/') ||
              src.includes('/pages/CodeUtilitiesPage/')) {
            return 'tools-text'
          }

          // Web/Dev tools
          if (src.includes('/pages/WebDevHelpersPage/') ||
              src.includes('/pages/ImageToolsPage/') ||
              src.includes('/pages/DevUtilsPage/') ||
              src.includes('/pages/TimeUtilsPage/')) {
            return 'tools-web'
          }

          // Data tools
          if (src.includes('/pages/NumberAnalyzerPage/') ||
              src.includes('/pages/UnitConverterPage/')) {
            return 'tools-data'
          }
        },
      },
    },
  },
})
