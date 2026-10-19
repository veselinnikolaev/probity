import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/auth';
import { AxiosError } from 'axios';
import { ROUTES } from '@/constants/routes';
import { authService } from '@/services/auth';
import styles from '@/pages/auth/auth.module.css';

interface LoginProps {
  onRegisterClick?: () => void;
}

export const Login: React.FC<LoginProps> = ({ onRegisterClick }) => {
  const [identifier, setIdentifier] = useState(''); // was: email
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [emailNotVerified, setEmailNotVerified] = useState(false);
  const [userEmail, setUserEmail] = useState('');

  const identifierRef = useRef<HTMLInputElement>(null); // was: emailRef
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    identifierRef.current?.focus();
  }, []);

  useEffect(() => {
    const state = location.state as { successMessage?: string } | null;
    if (state?.successMessage) {
      setSuccessMessage(state.successMessage);
      window.history.replaceState({}, '');
    }
  }, [location.state]);

  useEffect(() => {
    if (isAuthenticated) {
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? ROUTES.DASHBOARD;
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, location.state]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMessage('');
    setEmailNotVerified(false);

    if (!identifier || !password) { // was: !email
      setError('Please fill in all fields');
      return;
    }

    setIsLoading(true);
    try {
      await login({ identifier, password }); // was: { email, password }
    } catch (err) {
      const axiosError = err as AxiosError<{ message?: string; email?: string }>;
      if (axiosError.response?.status === 401) {
        const message = axiosError.response.data?.message?.toLowerCase() || '';
        const errorEmail = axiosError.response.data?.email;
        
        // Check if error is due to unverified email
        if (message.includes('verify') || message.includes('verified') || message.includes('confirmation')) {
          setEmailNotVerified(true);
          setUserEmail(errorEmail || identifier.includes('@') ? identifier : '');
          setError('Please verify your email before signing in');
        } else {
          setError('Invalid credentials'); // was: 'Invalid email or password'
        }
      } else if (axiosError.response?.status === 403) {
        // Some backends use 403 for unverified accounts
        const message = axiosError.response.data?.message?.toLowerCase() || '';
        const errorEmail = axiosError.response.data?.email;
        
        if (message.includes('verify') || message.includes('verified') || message.includes('confirmation')) {
          setEmailNotVerified(true);
          setUserEmail(errorEmail || identifier.includes('@') ? identifier : '');
          setError('Please verify your email before signing in');
        } else {
          setError('Access denied. Please check your account status.');
        }
      } else if (axiosError.response?.status === 429) {
        setError('Too many attempts. Please wait a moment.');
      } else if (!axiosError.response) {
        setError('Cannot reach server. Check your connection.');
      } else {
        setError(axiosError.response.data?.message ?? 'Something went wrong');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendVerification = async () => {
    if (!userEmail) {
      setError('No email address available to resend verification');
      return;
    }
    
    setIsLoading(true);
    try {
      await authService.resendVerification(userEmail);
      setSuccessMessage('Verification email sent! Please check your inbox.');
      setError('');
      setEmailNotVerified(false);
    } catch (err) {
      setError('Failed to resend verification email. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

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

        <div className={styles.card}>
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

          <form onSubmit={handleSubmit} className={styles.form}>
            {successMessage && (
                <div className={styles.successMessage}>{successMessage}</div>
            )}
            {error && (
              <div className={styles.errorMessage}>
                {error}
                {emailNotVerified && userEmail && (
                  <button
                    type="button"
                    onClick={handleResendVerification}
                    disabled={isLoading}
                    style={{
                      background: 'none',
                      border: 'none',
                      padding: '4px 0 0 0',
                      color: 'var(--pb-accent)',
                      cursor: isLoading ? 'not-allowed' : 'pointer',
                      fontSize: '13px',
                      textDecoration: 'underline',
                      display: 'block',
                      marginTop: '8px',
                      opacity: isLoading ? 0.5 : 1,
                    }}
                  >
                    {isLoading ? 'Sending...' : 'Resend verification email'}
                  </button>
                )}
              </div>
            )}

            {/* was: Email / type="email" */}
            <div className={styles.inputGroup}>
              <label htmlFor="identifier" className={styles.label}>Username or Email</label>
              <input
                  ref={identifierRef}
                  id="identifier"
                  type="text"
                  placeholder="Username or email"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className={styles.input}
                  autoComplete="username"
                  disabled={isLoading}
              />
            </div>

            <div className={styles.inputGroup}>
              <label htmlFor="password" className={styles.label}>Password</label>
              <div className={styles.passwordWrapper}>
                <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Enter your password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className={styles.input}
                    autoComplete="current-password"
                    disabled={isLoading}
                />
                <button
                    type="button"
                    className={styles.showPasswordBtn}
                    onClick={() => setShowPassword((v) => !v)}
                    tabIndex={-1}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                        <line x1="1" y1="1" x2="23" y2="23"/>
                      </svg>
                  ) : (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                        <circle cx="12" cy="12" r="3"/>
                      </svg>
                  )}
                </button>
              </div>
            </div>

            <button
                type="submit"
                disabled={isLoading}
                className={styles.primaryButton}
            >
              {isLoading ? 'Signing In...' : 'Sign In'}
            </button>
          </form>

          <div className={styles.divider}></div>

          <p className={styles.bottomText}>
            Don't have an account?{' '}
            <button onClick={onRegisterClick} className={styles.linkButton}>
              Register
            </button>
          </p>
        </div>
      </div>
  );
};
