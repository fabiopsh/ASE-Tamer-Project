import { Component, type ErrorInfo, type ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/** Catches unexpected rendering errors and offers a clean recovery screen. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled UI error:', error, info.componentStack);
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    this.setState({ error: null });
    window.location.assign('/');
  };

  render(): ReactNode {
    if (this.state.error) {
      return (
        <main className="page page--narrow" style={{ paddingTop: '14vh' }} role="alert">
          <section className="panel stack">
            <p className="eyebrow">Unexpected error</p>
            <h1>Something went wrong</h1>
            <p className="muted">
              An unexpected error interrupted this screen. You can reload the page or return to the home camp.
            </p>
            {this.state.error.message && (
              <p className="subtle text-sm" style={{ overflowWrap: 'anywhere' }}>
                {this.state.error.message}
              </p>
            )}
            <div className="cluster">
              <button type="button" className="btn btn--primary" onClick={this.handleReload}>
                Reload page
              </button>
              <button type="button" className="btn btn--ghost" onClick={this.handleGoHome}>
                Go home
              </button>
            </div>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
