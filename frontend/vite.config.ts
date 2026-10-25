import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    // Straight into Maven's output, so `mvn verify` packages it into the jar with nothing copied by hand.
    // emptyOutDir is needed because the folder is outside the Vite root, where Vite will not clear it by default.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // Fixed, because Google's dev redirect URI names :5173 (phase 3); a silent move to :5174 would break login.
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8082',
    },
  },
});
