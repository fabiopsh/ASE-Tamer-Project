/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Gateway origin, e.g. https://localhost:8443 (no trailing slash). */
  readonly VITE_GATEWAY_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
