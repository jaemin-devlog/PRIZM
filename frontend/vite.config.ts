import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', 'PRIZM_FRONTEND_')
  const backendTarget =
    env.PRIZM_FRONTEND_PROXY_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': backendTarget,
        '/actuator': backendTarget,
      },
    },
  }
})
