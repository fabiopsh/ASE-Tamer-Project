import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Content-Security-Policy injected as a <meta> tag in the production build only
 * (docs/plan/09-frontend.md, Security). The dev server needs inline scripts for
 * hot reload, so the policy is not applied there. The API origin comes from
 * VITE_GATEWAY_URL, so a build for another gateway host gets a matching policy.
 */
function csp(gatewayUrl: string): string {
  const api = new URL(gatewayUrl).origin;
  return (
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
    `img-src 'self' ${api}; connect-src ${api}; ` +
    "frame-ancestors 'none'; base-uri 'self'"
  );
}

function cspPlugin(gatewayUrl: string): Plugin {
  return {
    name: 'tamer-csp',
    apply: 'build',
    transformIndexHtml() {
      return [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: csp(gatewayUrl) },
          injectTo: 'head-prepend',
        },
      ];
    },
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), cspPlugin(loadEnv(mode, process.cwd(), 'VITE_').VITE_GATEWAY_URL || 'https://localhost:8443')],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
  build: {
    // Never inline fonts or images as data: URIs: the CSP (default-src 'self') would block them.
    assetsInlineLimit: 0,
  },
}));
