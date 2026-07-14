import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_BACKEND_PROXY_TARGET || 'https://msg-drag-chargers-twist.trycloudflare.com'
  return ({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': { target: backendTarget, changeOrigin: true, secure: true },
      '/ws': { target: backendTarget.replace(/^http/, 'ws'), ws: true, changeOrigin: true },
    },
  },
  })
})
