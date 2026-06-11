import React, { useState, useMemo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { InputField } from '../probity/InputField';
import { ProbityButton } from '../probity/ProbityButton';
import { ROUTES } from '@/constants/routes';
import { authService } from '@/services/auth';
import styles from '@/pages/auth/auth.module.css';

interface RegisterProps {
  onSignInClick?: () => void;
}

const calculatePasswordStrength = (password: string): number => {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 8) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;
  return Math.min(score, 4);
};

const basicEmailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Step = 1 | 2 | 'confirmation';

export const Register: React.FC<RegisterProps> = ({ onSignInClick }) => {
  const [step, setStep] = useState<Step>(1);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [resendCountdown, setResendCountdown] = useState(0);
  const [errors, setErrors] = useState<{ [key: string]: string }>({});
  const [fieldErrors, setFieldErrors] = useState<{ [key: string]: { state: 'error'; message: string } }>({});
  const [resendSuccess, setResendSuccess] = useState(false);

  const navigate = useNavigate();

  // Password strength for step 2
  const passwordStrength = useMemo(() => calculatePasswordStrength(password), [password]);
  const passwordsMatch = password && confirmPassword && password === confirmPassword;
  const passwordValid = passwordStrength === 4 && passwordsMatch;

  // Resend countdown timer
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (resendCountdown > 0) {
      interval = setInterval(() => {
        setResendCountdown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [resendCountdown]);

  // Reset resend success message after 3 seconds
  useEffect(() => {
    if (resendSuccess) {
      const timeout = setTimeout(() => setResendSuccess(false), 3000);
      return () => clearTimeout(timeout);
    }
  }, [resendSuccess]);

  const validateStep1 = (): boolean => {
    const newErrors: { [key: string]: string } = {};
    if (!firstName.trim()) newErrors['first-name'] = 'First name is required';
    if (!lastName.trim()) newErrors['last-name'] = 'Last name is required';
    if (!username.trim()) newErrors['username'] = 'Username is required';
    if (!email.trim()) {
      newErrors['email'] = 'Email is required';
    } else if (!basicEmailRegex.test(email)) {
      newErrors['email'] = 'Please enter a valid email';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleStep1Continue = () => {
    if (validateStep1()) {
      setErrors({});
      setFieldErrors({});
      setStep(2);
    }
  };

  const handleStep2Back = () => {
    setErrors({});
    setStep(1);
  };

  const handleCreateAccount = async () => {
    if (!passwordValid) {
      setErrors({ general: 'Password does not meet all requirements' });
      return;
    }

    setIsLoading(true);
    setErrors({});
    try {
      await authService.register({
        firstName,
        lastName,
        username,
        email,
        password,
      });
      setStep('confirmation');
      setResendCountdown(300);
    } catch (err: any) {
      if (err.response?.status === 409) {
        const body = err.response.data;
        if (body && body.includes('Username')) {
          setFieldErrors({ username: { state: 'error', message: 'Username already taken' } });
          setStep(1);
        } else if (body && body.includes('Email')) {
          setFieldErrors({ email: { state: 'error', message: 'Email already registered' } });
          setStep(1);
        } else {
          setErrors({ general: 'Registration failed. Please try again.' });
        }
      } else {
        setErrors({ general: 'Registration failed. Please try again.' });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendEmail = async () => {
    try {
      await authService.resendVerification(email);
      setResendSuccess(true);
      setResendCountdown(300);
    } catch (err) {
      // Error handling - could show inline error
    }
  };

  const formatCountdown = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
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

        {/* Progress indicator - only show on steps 1 and 2 */}
        {step !== 'confirmation' && (
          <div className={styles.progressIndicator}>
            {[1, 2].map((dotStep) => (
              <div
                key={dotStep}
                className={`${styles.progressDot} ${step === dotStep ? styles.progressDotActive : ''}`}
              ></div>
            ))}
          </div>
        )}

        <p className={styles.tagline}>Portfolio Risk Intelligence</p>

        {/* Step 1: Identity & Email */}
        {step === 1 && (
          <div className={styles.stepContent}>
            <div>
              <h2 className={styles.cardTitle}>Create your account</h2>
              <p className={styles.cardSubtitle}>Step 1 of 2 — Who are you?</p>
            </div>

            <div className={styles.form}>
              <div className={styles.inputRowContainer}>
                <div style={{ flex: 1 }}>
                  <InputField
                    label="First Name"
                    id="first-name"
                    placeholder="John"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    inputState={errors['first-name'] ? 'error' : fieldErrors['first-name']?.state || 'default'}
                    errorMessage={errors['first-name'] || fieldErrors['first-name']?.message}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <InputField
                    label="Last Name"
                    id="last-name"
                    placeholder="Doe"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    inputState={errors['last-name'] ? 'error' : fieldErrors['last-name']?.state || 'default'}
                    errorMessage={errors['last-name'] || fieldErrors['last-name']?.message}
                  />
                </div>
              </div>

              <InputField
                label="Username"
                id="username"
                placeholder="johndoe"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                inputState={errors['username'] ? 'error' : fieldErrors['username']?.state || 'default'}
                errorMessage={errors['username'] || fieldErrors['username']?.message}
              />

              <InputField
                label="Email"
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                inputState={errors['email'] ? 'error' : fieldErrors['email']?.state || 'default'}
                errorMessage={errors['email'] || fieldErrors['email']?.message}
              />

              <ProbityButton
                variant="primary"
                size="lg"
                style={{ width: '100%' }}
                onClick={handleStep1Continue}
              >
                Continue →
              </ProbityButton>
            </div>

            <div className={styles.divider}></div>

            <p className={styles.bottomText}>
              Already have an account?{' '}
              <button onClick={onSignInClick} className={styles.linkButton}>
                Sign in
              </button>
            </p>
          </div>
        )}

        {/* Step 2: Password */}
        {step === 2 && (
          <div className={styles.stepContent}>
            <div>
              <h2 className={styles.cardTitle}>Secure your account</h2>
              <p className={styles.cardSubtitle}>Step 2 of 2 — Choose a password</p>
            </div>

            <div className={styles.form}>
              <div>
                <div className={styles.inputGroup}>
                  <label htmlFor="password" className={styles.label}>Password</label>
                  <div className={styles.passwordInputWrapper}>
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="Create a strong password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className={`${styles.input} ${errors['password'] ? styles.inputError : ''}`}
                      disabled={isLoading}
                    />
                    <button
                      type="button"
                      className={styles.eyeToggleBtn}
                      onClick={() => setShowPassword(!showPassword)}
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
                  {errors['password'] && (
                    <span className={styles.errorText}>{errors['password']}</span>
                  )}
                </div>

                {password && (
                  <div className={styles.strengthContainer}>
                    <div className={styles.strengthBars}>
                      {[...Array(4)].map((_, i) => (
                        <div
                          key={i}
                          className={styles.strengthBar}
                          style={{
                            backgroundColor:
                              i < passwordStrength
                                ? i === 0
                                  ? 'var(--pb-danger)'
                                  : i < 3
                                  ? 'var(--pb-warning)'
                                  : 'var(--pb-success)'
                                : 'var(--pb-border)',
                          }}
                        ></div>
                      ))}
                    </div>
                    <span className={styles.strengthLabel}>
                      {passwordStrength === 0 ? 'Weak' : passwordStrength <= 2 ? 'Fair' : passwordStrength === 3 ? 'Good' : 'Strong'}
                    </span>
                  </div>
                )}
              </div>

              <div className={styles.inputGroup}>
                <label htmlFor="confirm-password" className={styles.label}>Confirm Password</label>
                <div className={styles.passwordInputWrapper}>
                  <input
                    id="confirm-password"
                    type={showConfirmPassword ? 'text' : 'password'}
                    placeholder="Confirm your password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className={`${styles.input} ${errors['confirm-password'] ? styles.inputError : ''}`}
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    className={styles.eyeToggleBtn}
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
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
                {errors['confirm-password'] && (
                  <span className={styles.errorText}>{errors['confirm-password']}</span>
                )}
              </div>

              {errors['general'] && (
                <div style={{
                  padding: '12px',
                  marginTop: '16px',
                  border: '1px solid var(--pb-danger)',
                  backgroundColor: 'rgba(239,68,68,0.08)',
                  borderRadius: '8px',
                  fontSize: '14px',
                  color: 'var(--pb-text-primary)',
                }}>
                  {errors['general']}
                </div>
              )}

              <div className={styles.buttonRow}>
                <ProbityButton
                  variant="ghost"
                  size="lg"
                  onClick={handleStep2Back}
                >
                  ← Back
                </ProbityButton>
                <ProbityButton
                  variant="primary"
                  size="lg"
                  onClick={handleCreateAccount}
                  loading={isLoading}
                  disabled={isLoading}
                  style={{ flex: 1 }}
                >
                  Create Account
                </ProbityButton>
              </div>
            </div>
          </div>
        )}

        {/* Confirmation Screen */}
        {step === 'confirmation' && (
          <div className={styles.stepContent}>
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <div style={{ marginBottom: '24px' }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--pb-success)" strokeWidth="2.5">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                  <polyline points="22 4 12 14.01 9 11.01" />
                </svg>
              </div>

              <h2 className={styles.cardTitle} style={{ textAlign: 'center', marginBottom: '8px' }}>
                Check your inbox
              </h2>

              <p style={{
                fontFamily: 'DM Sans, sans-serif',
                fontSize: '14px',
                color: 'var(--pb-text-secondary)',
                textAlign: 'center',
                marginBottom: '8px',
                lineHeight: '1.5',
              }}>
                We sent a verification link to{' '}
                <span style={{ color: 'var(--pb-text-primary)', fontWeight: 'bold' }}>{email}</span>.
                Click the link to activate your account.
              </p>

              <p style={{
                fontFamily: 'DM Sans, sans-serif',
                fontSize: '12px',
                color: 'var(--pb-text-muted)',
                textAlign: 'center',
                marginBottom: '20px',
              }}>
                The link expires in 24 hours. Check your spam folder if you don't see it.
              </p>

              <div style={{
                width: '100%',
                height: '1px',
                backgroundColor: 'var(--pb-border)',
                margin: '20px 0',
              }}></div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px', marginBottom: '20px' }}>
                <span style={{
                  fontFamily: 'DM Sans, sans-serif',
                  fontSize: '13px',
                  color: 'var(--pb-text-secondary)',
                }}>
                  Didn't get it?
                </span>
                {resendCountdown > 0 ? (
                  <span style={{
                    fontFamily: 'IBM Plex Mono, monospace',
                    fontSize: '13px',
                    color: 'var(--pb-text-muted)',
                  }}>
                    Resend in {formatCountdown(resendCountdown)}
                  </span>
                ) : resendSuccess ? (
                  <span style={{
                    fontFamily: 'DM Sans, sans-serif',
                    fontSize: '13px',
                    color: 'var(--pb-success)',
                  }}>
                    Sent!
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={handleResendEmail}
                    style={{
                      background: 'none',
                      border: 'none',
                      padding: 0,
                      fontFamily: 'DM Sans, sans-serif',
                      fontSize: '13px',
                      color: 'var(--pb-accent)',
                      cursor: 'pointer',
                      textDecoration: 'none',
                    }}
                  >
                    Resend email
                  </button>
                )}
              </div>

              <button
                onClick={() => navigate(ROUTES.LOGIN)}
                className={styles.linkButton}
                style={{
                  display: 'block',
                  margin: '0 auto',
                  textAlign: 'center',
                }}
              >
                Back to sign in
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
