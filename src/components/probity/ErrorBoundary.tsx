import React, { Component, ErrorInfo, ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          padding: '24px',
          backgroundColor: 'var(--pb-surface)',
          fontFamily: 'var(--font-body)',
        }}>
          <div style={{
            maxWidth: '480px',
            textAlign: 'center',
          }}>
            <div style={{ fontSize: '64px', marginBottom: '16px' }}>⚠️</div>
            <h1 style={{
              fontSize: '24px',
              fontWeight: 700,
              color: 'var(--pb-text-primary)',
              marginBottom: '12px',
              fontFamily: 'var(--font-display)',
            }}>
              Something went wrong
            </h1>
            <p style={{
              fontSize: '14px',
              color: 'var(--pb-text-secondary)',
              marginBottom: '24px',
              lineHeight: '1.5',
            }}>
              An unexpected error occurred. Please try refreshing the page.
            </p>
            <button
              onClick={this.handleReload}
              style={{
                padding: '10px 20px',
                fontSize: '14px',
                fontWeight: 600,
                color: 'var(--pb-text-primary)',
                backgroundColor: 'var(--pb-primary)',
                border: 'none',
                borderRadius: '6px',
                cursor: 'pointer',
                transition: 'opacity 0.12s',
              }}
              onMouseOver={(e) => { (e.currentTarget as HTMLElement).style.opacity = '0.9'; }}
              onMouseOut={(e) => { (e.currentTarget as HTMLElement).style.opacity = '1'; }}
            >
              Reload Page
            </button>
            {this.state.error && (
              <details style={{
                marginTop: '24px',
                textAlign: 'left',
                fontSize: '12px',
                color: 'var(--pb-text-tertiary)',
              }}>
                <summary style={{ cursor: 'pointer', marginBottom: '8px' }}>
                  Error details
                </summary>
                <pre style={{
                  padding: '12px',
                  backgroundColor: 'var(--pb-surface-elevated)',
                  border: '1px solid var(--pb-border)',
                  borderRadius: '6px',
                  overflow: 'auto',
                  whiteSpace: 'pre-wrap',
                }}>
                  {this.state.error.toString()}
                </pre>
              </details>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
