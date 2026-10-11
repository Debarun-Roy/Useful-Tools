const LOCAL_ORIGIN = 'http://localhost:5173'

/** Canonicalize page visits, never credentials or API requests. Dev server only. */
export function localOriginPlugin() {
  return {
    name: 'localhost-page-origin',
    apply: 'serve',
    configureServer(server) {
      // Install before Vite's proxy and HTML middleware. A fixed destination
      // avoids trusting Host/Forwarded headers when constructing redirects.
      server.middlewares.use((request, response, next) => {
        const path = request.url || '/'
        const api = /^\/api(?:[/?]|$)/i.test(path)
        if (request.headers.host !== '127.0.0.1:5173'
            || !['GET', 'HEAD'].includes(request.method) || api
            || !path.startsWith('/')) {
          next()
          return
        }
        response.statusCode = 302
        response.setHeader('Location', LOCAL_ORIGIN + path)
        response.setHeader('Cache-Control', 'no-store')
        response.end()
      })
    },
  }
}
