import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// The dev server proxies to the backend so the browser only ever talks to one
// origin (no CORS). The same rules are mirrored in web/nginx.conf for Docker.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8081',
      '/kb': 'http://localhost:8001',
      '/health': 'http://localhost:8001',
    },
  },
  preview: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8081',
      '/kb': 'http://localhost:8001',
      '/health': 'http://localhost:8001',
    },
  },
})
