import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import './styles/fonts.css';
import './styles/tokens.css';
import './styles/base.css';
import './styles/ui.css';
import { App } from './App';
import { ToastProvider } from './components/ToastProvider';
import { SessionProvider } from './session/SessionContext';
import './styles/amber.css';

function renderApp() {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <BrowserRouter>
        <ToastProvider>
          <SessionProvider>
            <App />
          </SessionProvider>
        </ToastProvider>
      </BrowserRouter>
    </StrictMode>,
  );
}

// Navigation measures its links on mount. Load their face first so fallback metrics
// cannot change the initial scroll offset; a missing font must never block the app.
void document.fonts.load('700 15px "Amber UI"').then(renderApp, renderApp);
