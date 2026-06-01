import React from 'react';

interface ErrorStateProps {
  message: string;
  onRetry?: () => void;
  size?: 'sm' | 'md' | 'lg';
}

export function ErrorState({ message, onRetry, size = 'md' }: ErrorStateProps) {
  const paddings = { sm: '32px 24px', md: '48px 32px', lg: '64px 48px' };
  const iconSizes = { sm: '32px', md: '40px', lg: '48px' };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: paddings[size],
        border: '1px solid var(--pb-error-border)',
        borderRadius: '8px',
        backgroundColor: 'rgba(220, 38, 38, 0.05)',
        gap: '12px',
        width: '100%',
      }}
    >
      <div
        style={{
          width: iconSizes[size],
          height: iconSizes[size],
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--pb-error)',
          fontSize: iconSizes[size],
          marginBottom: '4px',
        }}
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <h4
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: size === 'sm' ? '13px' : size === 'md' ? '15px' : '17px',
            fontWeight: 700,
            color: 'var(--pb-error)',
            margin: 0,
          }}
        >
          Something went wrong
        </h4>
        <p
          style={{
            fontFamily: 'var(--font-body)',
            fontSize: size === 'sm' ? '12px' : '13px',
            color: 'var(--pb-text-secondary)',
            margin: 0,
            lineHeight: 1.6,
            maxWidth: '320px',
          }}
        >
          {message}
        </p>
      </div>

      {onRetry && (
        <button
          onClick={onRetry}
          style={{
            marginTop: '12px',
            padding: '8px 16px',
            backgroundColor: 'var(--pb-error)',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            fontFamily: 'var(--font-body)',
            fontSize: '13px',
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'opacity 0.2s',
          }}
          onMouseEnter={(e) => {
            (e.target as HTMLButtonElement).style.opacity = '0.9';
          }}
          onMouseLeave={(e) => {
            (e.target as HTMLButtonElement).style.opacity = '1';
          }}
        >
          Try Again
        </button>
      )}
    </div>
  );
}

export default ErrorState;
