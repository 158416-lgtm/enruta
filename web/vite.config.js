import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El proxy evita CORS: el navegador habla con el mismo origen y Vite reenvia.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // El tunel (y la LAN) llegan con un Host distinto a localhost; Vite lo bloquea por defecto.
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      },
      '/osrm': {
        target: 'http://localhost:5000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/osrm/, '')
      },
      '/ws': {
        target: 'ws://localhost:8084',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
