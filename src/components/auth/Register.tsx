import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { InputField } from '../probity/InputField';
import { ProbityButton } from '../probity/ProbityButton';
import { ROUTES } from '@/constants/routes';
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

export const Register: React.FC<RegisterProps> = ({ onSignInClick }) => {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [isLoading, setIsLoading] = useState(false);
  const [resendCountdown, setResendCountdown] = useState(0);
  const [errors, setErrors] = useState<{ [key: string]: string }>({});
  const [codeSent, setCodeSent] = useState(false);

  const codeInputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const navigate = useNavigate();

  // Password strength for step 2
  const passwordStrength = useMemo(() => calculatePasswordStrength(password), [password]);
  const passwordsMatch = password && confirmPassword && password === confirmPassword;

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

  // Send verification code when arriving at step 3
  useEffect(() => {
    if (step === 3 && !codeSent) {
      sendVerificationCode();
    }
  }, [step, codeSent]);

  const sendVerificationCode = async () => {
    try {
      await fetch('/api/v1/auth/send-verification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      setCodeSent(true);
      setResendCountdown(60);
    } catch (err) {
      setErrors({ code: 'Failed to send verification code' });
    }
  };

  const validateStep1 = (): boolean => {
    const newErrors: { [key: string]: string } = {};
    if (!firstName.trim()) newErrors['first-name'] = 'First name is required';
    if (!lastName.trim()) newErrors['last-name'] = 'Last name is required';
    if (!email.trim()) {
      newErrors['email'] = 'Email is required';
    } else if (!basicEmailRegex.test(email)) {
      newErrors['email'] = 'Please enter a valid email';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const validateStep2 = (): boolean => {
    const newErrors: { [key: string]: string } = {};
    if (!password) {
      newErrors['password'] = 'Password is required';
    } else if (password.length < 8) {
      newErrors['password'] = 'Password must be at least 8 characters';
    }
    if (!confirmPassword) {
      newErrors['confirm-password'] = 'Please confirm your password';
    } else if (password !== confirmPassword) {
      newErrors['confirm-password'] = 'Passwords do not match';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleStep1Continue = () => {
    if (validateStep1()) {
      setErrors({});
      setStep(2);
    }
  };

  const handleStep2Continue = () => {
    if (validateStep2()) {
      setErrors({});
      setStep(3);
    }
  };

  const handleStep2Back = () => {
    setErrors({});
    setStep(1);
  };

  const handleStep3Back = () => {
    setErrors({});
    setStep(2);
  };

  const handleCodeChange = (index: number, value: string) => {
    // Only allow digits
    if (!/^\d*$/.test(value)) return;
    const newCode = [...code];
    newCode[index] = value.slice(0, 1); // Keep only one digit
    setCode(newCode);

    // Auto-focus next field
    if (value && index < 5) {
      codeInputRefs.current[index + 1]?.focus();
    }
  };

  const handleCodeKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      codeInputRefs.current[index - 1]?.focus();
    }
  };

  const handleCodePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    const newCode = [...code];
    for (let i = 0; i < pasted.length; i++) {
      newCode[i] = pasted[i];
    }
    setCode(newCode);
    if (pasted.length === 6) {
      codeInputRefs.current[5]?.blur();
    }
  };

  const handleVerifyAndCreate = async () => {
    const codeString = code.join('');
    if (codeString.length !== 6) {
      setErrors({ code: 'Please enter all 6 digits' });
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName,
          lastName,
          email,
          password,
          verificationCode: codeString,
        }),
      });

      if (response.status === 200) {
        navigate(ROUTES.DASHBOARD, { replace: true });
      } else if (response.status === 400) {
        const data = await response.json();
        setErrors({ code: data.message || 'Invalid code' });
      } else {
        setErrors({ code: 'Something went wrong' });
      }
    } catch (err) {
      setErrors({ code: 'Failed to verify code' });
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

        {/* Progress indicator */}
        <div className={styles.progressIndicator}>
          {[1, 2, 3].map((dotStep) => (
            <div
              key={dotStep}
              className={`${styles.progressDot} ${step === dotStep ? styles.progressDotActive : ''}`}
            ></div>
          ))}
        </div>

        <p className={styles.tagline}>Portfolio Risk Intelligence</p>

        {/* Step 1: Identity & Email */}
        {step === 1 && (
          <div className={styles.stepContent}>
            <div>
              <h2 className={styles.cardTitle}>Create your account</h2>
              <p className={styles.cardSubtitle}>Step 1 of 3 — Who are you?</p>
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
                    inputState={errors['first-name'] ? 'error' : 'default'}
                    errorMessage={errors['first-name']}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <InputField
                    label="Last Name"
                    id="last-name"
                    placeholder="Doe"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    inputState={errors['last-name'] ? 'error' : 'default'}
                    errorMessage={errors['last-name']}
                  />
                </div>
              </div>

              <InputField
                label="Email"
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                inputState={errors['email'] ? 'error' : 'default'}
                errorMessage={errors['email']}
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
              <p className={styles.cardSubtitle}>Step 2 of 3 — Choose a password</p>
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
                  onClick={handleStep2Continue}
                  style={{ flex: 1 }}
                >
                  Continue →
                </ProbityButton>
              </div>
            </div>
          </div>
        )}

        {/* Step 3: Email Verification */}
        {step === 3 && (
          <div className={styles.stepContent}>
            <div>
              <h2 className={styles.cardTitle}>Check your email</h2>
              <p className={styles.cardSubtitle}>
                We sent a 6-digit code to <span className={styles.emailHighlight}>{email}</span>
              </p>
            </div>

            <div className={styles.form}>
              <div className={styles.codeInputContainer}>
                {code.map((digit, i) => (
                  <input
                    key={i}
                    ref={(el) => (codeInputRefs.current[i] = el)}
                    type="text"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleCodeChange(i, e.target.value)}
                    onKeyDown={(e) => handleCodeKeyDown(i, e)}
                    onPaste={i === 0 ? handleCodePaste : undefined}
                    className={styles.codeInput}
                    disabled={isLoading}
                    aria-label={`Digit ${i + 1}`}
                  />
                ))}
              </div>

              {errors['code'] && (
                <p className={styles.codeError}>{errors['code']}</p>
              )}

              <div className={styles.resendContainer}>
                <span className={styles.resendLabel}>Didn't receive it? </span>
                {resendCountdown > 0 ? (
                  <span className={styles.resendCountdown}>
                    Resend in {resendCountdown}s
                  </span>
                ) : (
                  <button
                    type="button"
                    className={styles.resendButton}
                    onClick={sendVerificationCode}
                    disabled={isLoading}
                  >
                    Resend code
                  </button>
                )}
              </div>

              <div className={styles.buttonRow}>
                <ProbityButton
                  variant="ghost"
                  size="lg"
                  onClick={handleStep3Back}
                  disabled={isLoading}
                >
                  ← Back
                </ProbityButton>
                <ProbityButton
                  variant="primary"
                  size="lg"
                  onClick={handleVerifyAndCreate}
                  disabled={code.join('').length !== 6 || isLoading}
                  loading={isLoading}
                  style={{ flex: 1 }}
                >
                  Verify & Create Account
                </ProbityButton>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
