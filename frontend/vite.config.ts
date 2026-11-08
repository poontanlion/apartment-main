import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const localEnv = loadEnv(mode, process.cwd(), 'VITE_')
  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 3001,
      strictPort: true,
      proxy: {
        '/api': {
          target: process.env.VITE_API_TARGET || localEnv.VITE_API_TARGET || 'http://localhost:8083',
          changeOrigin: true,
        },
      },
    },
  }
})
