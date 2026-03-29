import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:4000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (_, req) => {
            console.log(`\x1b[36m→ ${req.method} ${req.url}\x1b[0m`)
          })
          proxy.on('proxyRes', (res, req) => {
            const color = res.statusCode < 400 ? '\x1b[32m' : '\x1b[31m'
            console.log(`${color}← ${res.statusCode} ${req.method} ${req.url}\x1b[0m`)
          })
        },
      },
    },
  },
})
