import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Content-Security-Policy injected as a <meta> tag in the production build only
 * (docs/plan/09-frontend.md, Security). The dev server needs inline scripts for
 * hot reload, so the policy is not applied there.
 */
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
  "img-src 'self' https://localhost:8443; connect-src https://localhost:8443; " +
  "frame-ancestors 'none'; base-uri 'self'";

function cspPlugin(): Plugin {
  return {
    name: 'tamer-csp',
    apply: 'build',
    transformIndexHtml() {
      return [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: CSP },
          injectTo: 'head-prepend',
        },
      ];
    },
  };
}

export default defineConfig({
  plugins: [react(), cspPlugin()],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
  build: {
    // Never inline fonts or images as data: URIs: the CSP (default-src 'self') would block them.
    assetsInlineLimit: 0,
  },
});
