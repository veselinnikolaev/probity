import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { AxiosError } from 'axios';
import styles from './auth.module.css';

interface RegisterProps {
  onSignInClick?: () => void;
}

const calculatePasswordStrength = (
  password: string
): { score: number; level: string; color: string } => {
  if (!password) return { score: 0, level: '', color: '' };

  let score = 0;
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (password.length >= 16) score += 1;
  if (/[a-z]/.test(password)) score += 1;
  if (/[A-Z]/.test(password)) score += 1;
  if (/[0-9]/.test(password)) score += 1;
  if (/[^a-zA-Z0-9]/.test(password)) score += 1;

  let level = 'Weak';
  let color = '#EF4444';
  if (score <= 2) { level = 'Weak'; color = '#EF4444'; }
  else if (score <= 4) { level = 'Fair'; color = '#F59E0B'; }
  else if (score <= 6) { level = 'Good'; color = '#3B82F6'; }
  else { level = 'Strong'; color = '#10B981'; }

  return { score: Math.min(score, 7), level, color };
};

export const Register: React.FC<RegisterProps> = ({ onSignInClick }) => {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const fullNameRef = useRef<HTMLInputElement>(null);
  const { register } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    fullNameRef.current?.focus();
  }, []);

  const passwordStrength = useMemo(() => calculatePasswordStrength(password), [password]);
  const passwordsMatch = password && confirmPassword && password === confirmPassword;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!fullName || !email || !password || !confirmPassword) {
      setError('Please fill in all fields');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    if (passwordStrength.score <= 2) {
      setError('Password is too weak. Please use a stronger password');
      return;
    }

    setIsLoading(true);
    try {
      await register({ fullName, email, password });
      navigate('/login', {
        state: { successMessage: 'Account created! Please sign in.' },
        replace: true,
      });
    } catch (err) {
      const axiosError = err as AxiosError<{ message?: string }>;
      if (axiosError.response?.status === 409) {
        setError('An account with this email already exists');
      } else if (axiosError.response?.status === 422) {
        setError(axiosError.response.data?.message ?? 'Invalid data provided');
      } else if (!axiosError.response) {
        setError('Cannot reach server. Check your connection.');
      } else {
        setError(axiosError.response.data?.message ?? 'Something went wrong');
      }
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

        <form onSubmit={handleSubmit} className={styles.form}>
          {error && <div className={styles.errorMessage}>{error}</div>}

          <div className={styles.inputGroup}>
            <label htmlFor="fullName" className={styles.label}>Full Name</label>
            <input
              ref={fullNameRef}
              id="fullName"
              type="text"
              placeholder="Your name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className={styles.input}
              autoComplete="name"
              disabled={isLoading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="email" className={styles.label}>Email</label>
            <input
              id="email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={styles.input}
              autoComplete="email"
              disabled={isLoading}
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="password" className={styles.label}>Password</label>
            <div className={styles.passwordWrapper}>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Create a strong password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={styles.input}
                autoComplete="new-password"
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

            {password && (
              <div className={styles.strengthContainer}>
                <div className={styles.strengthBars}>
                  {[...Array(7)].map((_, i) => (
                    <div
                      key={i}
                      className={styles.strengthBar}
                      style={{
                        backgroundColor:
                          i < passwordStrength.score
                            ? passwordStrength.color
                            : 'var(--pb-border)',
                      }}
                    ></div>
                  ))}
                </div>
                <span className={styles.strengthLabel} style={{ color: passwordStrength.color }}>
                  {passwordStrength.level}
                </span>
              </div>
            )}
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="confirmPassword" className={styles.label}>Confirm Password</label>
            <div className={styles.passwordWrapper}>
              <input
                id="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Confirm your password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className={`${styles.input} ${
                  confirmPassword
                    ? passwordsMatch
                      ? styles.inputSuccess
                      : styles.inputError
                    : ''
                }`}
                autoComplete="new-password"
                disabled={isLoading}
              />
              <button
                type="button"
                className={styles.showPasswordBtn}
                onClick={() => setShowConfirmPassword((v) => !v)}
                tabIndex={-1}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                {showConfirmPassword ? (
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
            {confirmPassword && passwordsMatch && (
              <p className={styles.successText}>✓ Passwords match</p>
            )}
            {confirmPassword && !passwordsMatch && (
              <p className={styles.errorText}>Passwords do not match</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isLoading || !passwordsMatch}
            className={styles.primaryButton}
          >
            {isLoading ? 'Creating Account...' : 'Create Account'}
          </button>
        </form>

        <div className={styles.divider}></div>

        <p className={styles.bottomText}>
          Already have an account?{' '}
          <button onClick={onSignInClick} className={styles.linkButton}>
            Sign In
          </button>
        </p>
      </div>
    </div>
  );
};