import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El proxy evita CORS: el navegador habla con el mismo origen y Vite reenvia al gateway.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      }
    }
  }
})
