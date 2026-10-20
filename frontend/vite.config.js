import { defineConfig } from 'vite';

const backend = process.env.PAQRAP_API_TARGET || 'http://127.0.0.1:8080';

export default defineConfig({
  server: {
    proxy: {
      '/api': backend,
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true }
    }
  }
});
