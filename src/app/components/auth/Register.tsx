import React, { useState, useMemo } from 'react';
import styles from './auth.module.css';

interface RegisterProps {
  onSignInClick?: () => void;
}

const calculatePasswordStrength = (password: string): { score: number; level: string; color: string } => {
  let score = 0;
  let level = 'Weak';
  let color = '#EF4444';

  if (!password) return { score: 0, level: '', color: '' };

  // Length
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (password.length >= 16) score += 1;

  // Character types
  if (/[a-z]/.test(password)) score += 1;
  if (/[A-Z]/.test(password)) score += 1;
  if (/[0-9]/.test(password)) score += 1;
  if (/[^a-zA-Z0-9]/.test(password)) score += 1;

  if (score <= 2) {
    level = 'Weak';
    color = '#EF4444';
  } else if (score <= 4) {
    level = 'Fair';
    color = '#F59E0B';
  } else if (score <= 6) {
    level = 'Good';
    color = '#3B82F6';
  } else {
    level = 'Strong';
    color = '#10B981';
  }

  return { score: Math.min(score, 7), level, color };
};

export const Register: React.FC<RegisterProps> = ({ onSignInClick }) => {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const passwordStrength = useMemo(() => calculatePasswordStrength(password), [password]);
  const passwordsMatch = password && confirmPassword && password === confirmPassword;

  const handleSubmit = (e: React.FormEvent) => {
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
    // Simulate API call
    setTimeout(() => {
      setIsLoading(false);
      console.log('Register:', { fullName, email, password });
    }, 1000);
  };

  return (
    <div className={styles.authContainer}>
      {/* Animated background */}
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

      {/* Noise texture */}
      <div className={styles.noiseMask}></div>

      {/* Card content */}
      <div className={`${styles.card} ${styles.registerCard}`}>
        {/* Glow border effect */}
        <div className={styles.glowBorder}></div>

        {/* Wordmark */}
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

        {/* Tagline */}
        <p className={styles.tagline}>Portfolio Risk Intelligence</p>

        {/* Form */}
        <form onSubmit={handleSubmit} className={styles.form}>
          {error && <div className={styles.errorMessage}>{error}</div>}

          {/* Full name input */}
          <div className={styles.inputGroup}>
            <label htmlFor="fullName" className={styles.label}>Full Name</label>
            <input
              id="fullName"
              type="text"
              placeholder="Your name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className={styles.input}
            />
          </div>

          {/* Email input */}
          <div className={styles.inputGroup}>
            <label htmlFor="email" className={styles.label}>Email</label>
            <input
              id="email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={styles.input}
            />
          </div>

          {/* Password input */}
          <div className={styles.inputGroup}>
            <label htmlFor="password" className={styles.label}>Password</label>
            <input
              id="password"
              type="password"
              placeholder="Create a strong password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={styles.input}
            />

            {/* Password strength indicator */}
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

          {/* Confirm password input */}
          <div className={styles.inputGroup}>
            <label htmlFor="confirmPassword" className={styles.label}>Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={`${styles.input} ${
                confirmPassword && (passwordsMatch ? styles.inputSuccess : styles.inputError)
              }`}
            />
            {confirmPassword && passwordsMatch && (
              <p className={styles.successText}>✓ Passwords match</p>
            )}
            {confirmPassword && !passwordsMatch && (
              <p className={styles.errorText}>Passwords do not match</p>
            )}
          </div>

          {/* Submit button */}
          <button
            type="submit"
            disabled={isLoading || !passwordsMatch}
            className={styles.primaryButton}
          >
            {isLoading ? 'Creating Account...' : 'Create Account'}
          </button>
        </form>

        {/* Divider */}
        <div className={styles.divider}></div>

        {/* Sign in link */}
        <p className={styles.bottomText}>
          Already have an account?{' '}
          <button
            onClick={onSignInClick}
            className={styles.linkButton}
          >
            Sign In
          </button>
        </p>
      </div>
    </div>
  );
};
