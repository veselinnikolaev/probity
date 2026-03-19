import React, { useState } from 'react';
import styles from './auth.module.css';

interface LoginProps {
  onRegisterClick?: () => void;
}

export const Login: React.FC<LoginProps> = ({ onRegisterClick }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email || !password) {
      setError('Please fill in all fields');
      return;
    }

    setIsLoading(true);
    // Simulate API call
    setTimeout(() => {
      setIsLoading(false);
      console.log('Login:', { email, password });
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
      <div className={styles.card}>
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
              placeholder="Enter your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={styles.input}
            />
          </div>

          {/* Submit button */}
          <button
            type="submit"
            disabled={isLoading}
            className={styles.primaryButton}
          >
            {isLoading ? 'Signing In...' : 'Sign In'}
          </button>
        </form>

        {/* Divider */}
        <div className={styles.divider}></div>

        {/* Register link */}
        <p className={styles.bottomText}>
          Don't have an account?{' '}
          <button
            onClick={onRegisterClick}
            className={styles.linkButton}
          >
            Register
          </button>
        </p>
      </div>
    </div>
  );
};
