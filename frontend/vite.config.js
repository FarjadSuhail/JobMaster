import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy API calls to the Spring Boot backend so the app is same-origin in dev.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
