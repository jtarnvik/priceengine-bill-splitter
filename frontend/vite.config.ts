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
    // Fixed, because Google's dev redirect URI names :5173; a silent move to :5174 would break login.
    port: 5173,
    strictPort: true,
    // The auth paths too, so that dev is single-origin like production. Spring builds the OAuth2 redirect URI from
    // the request's Host header, which changeOrigin: false leaves at localhost:5173: Google then sends the browser
    // back through this proxy, and the session cookie belongs to :5173.
    proxy: {
      '/api': { target: 'http://localhost:8082', changeOrigin: false },
      '/oauth2': { target: 'http://localhost:8082', changeOrigin: false },
      '/login/oauth2': { target: 'http://localhost:8082', changeOrigin: false },
      '/logout': { target: 'http://localhost:8082', changeOrigin: false },
    },
  },
});
