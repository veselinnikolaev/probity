import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { ROUTES } from '@/constants/routes';
import { authService } from '@/services/auth';
import styles from '@/pages/auth/auth.module.css';

type VerificationState = 'loading' | 'success' | 'error';

export const VerifyEmail: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [state, setState] = useState<VerificationState>('loading');
  const token = searchParams.get('token');

  useEffect(() => {
    // Check if verification was already completed
    const savedState = localStorage.getItem('email_verification_state');
    if (savedState === 'success') {
      setState('success');
      return;
    }

    const verifyToken = async () => {
      if (!token) {
        setState('error');
        return;
      }

      try {
        await authService.verifyEmail(token);
        setState('success');
        localStorage.setItem('email_verification_state', 'success');
      } catch (err) {
        setState('error');
      }
    };

    verifyToken();
  }, [token]);

  return (
    <div className={styles.authContainer}>
      <div className={styles.animatedBg}>
        <div className={styles.gridLines}></div>
        <div className={styles.floatingParticles}>
          {[...Array(12)].map((_, i) => (
            <div
              key={i}
              className={styles.particle}
              style={{
                left: `${Math.random() * 100}%`,
                top: `${Math.random() * 100}%`,
                animationDelay: `${i * 0.3}s`,
              }}
            ></div>
          ))}
        </div>
      </div>

      <div className={styles.noiseMask}></div>

      <div className={`${styles.card} ${styles.registerCard}`}>
        <div className={styles.glowBorder}></div>

        <div className={styles.wordmark}>
          <div className={styles.wordmarkIcon}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
          </div>
          <span className={styles.wordmarkText}>PROBITY</span>
        </div>

        <p className={styles.tagline}>Portfolio Risk Intelligence</p>

        <div className={styles.stepContent}>
          {state === 'loading' && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <div style={{
                width: '24px',
                height: '24px',
                margin: '0 auto 16px',
                border: '2px solid var(--pb-border)',
                borderTopColor: 'var(--pb-accent)',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite',
              }}></div>
              <p style={{
                fontFamily: 'DM Sans, sans-serif',
                fontSize: '14px',
                color: 'var(--pb-text-secondary)',
              }}>
                Verifying your account…
              </p>
            </div>
          )}

          {state === 'success' && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <div style={{ marginBottom: '24px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--pb-success)" strokeWidth="2.5">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                  <polyline points="22 4 12 14.01 9 11.01" />
                </svg>
              </div>

              <h2 className={styles.cardTitle} style={{ textAlign: 'center', marginBottom: '8px' }}>
                Email verified!
              </h2>

              <p style={{
                fontFamily: 'DM Sans, sans-serif',
                fontSize: '14px',
                color: 'var(--pb-text-secondary)',
                textAlign: 'center',
                marginBottom: '24px',
              }}>
                Your account is ready.
              </p>

              <ProbityButton
                variant="primary"
                size="lg"
                style={{ width: '100%' }}
                onClick={() => {
                  localStorage.removeItem('email_verification_state');
                  navigate(ROUTES.LOGIN);
                }}
              >
                Go to Login
              </ProbityButton>
            </div>
          )}

          {state === 'error' && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <div style={{ marginBottom: '24px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--pb-danger)" strokeWidth="2.5">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </div>

              <h2 className={styles.cardTitle} style={{ textAlign: 'center', marginBottom: '8px' }}>
                Link expired or invalid
              </h2>

              <p style={{
                fontFamily: 'DM Sans, sans-serif',
                fontSize: '14px',
                color: 'var(--pb-text-secondary)',
                textAlign: 'center',
                marginBottom: '24px',
                lineHeight: '1.5',
              }}>
                This verification link has already been used or has expired.
              </p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <ProbityButton
                  variant="danger-ghost"
                  size="lg"
                  style={{ width: '100%' }}
                  onClick={() => navigate(ROUTES.LOGIN)}
                >
                  Request a new link
                </ProbityButton>

                <ProbityButton
                  variant="secondary"
                  size="lg"
                  style={{ width: '100%' }}
                  onClick={() => navigate(ROUTES.LOGIN)}
                >
                  Go to Login
                </ProbityButton>
              </div>
            </div>
          )}
        </div>
      </div>

      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};
