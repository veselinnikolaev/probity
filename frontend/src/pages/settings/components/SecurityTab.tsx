import React, { useState } from 'react';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { InputField } from '@/components/probity/InputField';
import { Modal } from '@/components/probity/Modal';
import { useProfile } from '@/hooks/settings';
import { useSessions } from '@/hooks/settings';
import styles from '../settings.module.css';

export function SecurityTab() {
  const { changePasswordMutation } = useProfile();
  const { sessions, revokeSessionMutation } = useSessions();
  
  const [showPasswordChange, setShowPasswordChange] = useState(false);
  const [revokeSessionId, setRevokeSessionId] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const handleChangePassword = async () => {
    setPasswordError(null);
    setPasswordSuccess(null);
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError('New passwords do not match');
      return;
    }
    try {
      await changePasswordMutation.execute(passwordForm);
      setPasswordSuccess('Password updated successfully');
      setShowPasswordChange(false);
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password');
    }
  };

  const handleRevokeSession = async (sessionId: string) => {
    try {
      await revokeSessionMutation.execute(sessionId);
      setRevokeSessionId(null);
    } catch (err) {
      // Error is handled by the mutation state
    }
  };

  return (
    <>
      <div className={styles.tabContent}>
        <div className={styles.contentHeader}>
          <h1 className={styles.contentTitle}>Security</h1>
          <p className={styles.contentSubtitle}>Manage your security settings</p>
        </div>

        <div className={styles.securitySection}>
          <div className={styles.sectionTitle}>Change Password</div>
          <p className={styles.sectionDescription}>
            Update your password to keep your account secure
          </p>

          {passwordSuccess && <p className={styles.successText}>{passwordSuccess}</p>}
          {passwordError && <p className={styles.errorText}>{passwordError}</p>}

          {!showPasswordChange ? (
            <ProbityButton variant="secondary" size="md" onClick={() => setShowPasswordChange(true)}>
              Change Password
            </ProbityButton>
          ) : (
            <div className={styles.passwordForm}>
              <InputField
                label="Current Password"
                type="password"
                value={passwordForm.currentPassword}
                onChange={(e) =>
                  setPasswordForm({ ...passwordForm, currentPassword: e.target.value })
                }
                placeholder="Enter current password"
              />
              <InputField
                label="New Password"
                type="password"
                value={passwordForm.newPassword}
                onChange={(e) =>
                  setPasswordForm({ ...passwordForm, newPassword: e.target.value })
                }
                placeholder="At least 8 characters"
              />
              <InputField
                label="Confirm Password"
                type="password"
                value={passwordForm.confirmPassword}
                onChange={(e) =>
                  setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })
                }
                placeholder="Repeat new password"
              />
              <div className={styles.formActions}>
                <ProbityButton
                  variant="primary"
                  size="md"
                  onClick={handleChangePassword}
                  disabled={changePasswordMutation.isPending}
                >
                  {changePasswordMutation.isPending ? 'Updating…' : 'Update Password'}
                </ProbityButton>
                <ProbityButton
                  variant="secondary"
                  size="md"
                  onClick={() => { setShowPasswordChange(false); setPasswordError(null); }}
                >
                  Cancel
                </ProbityButton>
              </div>
            </div>
          )}
        </div>

        <div className={styles.divider} />

        <div className={styles.securitySection}>
          <div className={styles.sectionTitle}>Active Sessions</div>
          <p className={styles.sectionDescription}>Manage your sessions across devices</p>

          {sessions.isLoading ? (
            <p className={styles.loadingText}>Loading sessions…</p>
          ) : sessions.error ? (
            <p className={styles.errorText}>{sessions.error}</p>
          ) : !sessions.data || sessions.data.length === 0 ? (
            <div className={styles.noSessions}>
              <p className={styles.noSessionsText}>No other active sessions</p>
            </div>
          ) : (
            <div className={styles.sessionsList}>
              {sessions.data.map((session) => (
                <div key={session.sessionId} className={styles.sessionItem}>
                  <div className={styles.sessionInfo}>
                    <p className={styles.sessionDevice}>{session.deviceHint}</p>
                    <p className={styles.sessionLocation}>{session.ipAddress}</p>
                    <p className={styles.sessionLastActive}>
                      Last active: {session.lastActiveAt}
                    </p>
                  </div>
                  <ProbityButton
                    variant="secondary"
                    size="sm"
                    onClick={() => setRevokeSessionId(session.sessionId)}
                    disabled={revokeSessionMutation.isPending}
                  >
                    {revokeSessionMutation.isPending ? 'Revoking…' : 'Revoke'}
                  </ProbityButton>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Revoke Session Modal */}
      {revokeSessionId && (
        <Modal
          open={!!revokeSessionId}
          onClose={() => setRevokeSessionId(null)}
          title="Revoke Session"
          size="sm"
          footer={
            <>
              <ProbityButton variant="secondary" size="md" onClick={() => setRevokeSessionId(null)}>
                Cancel
              </ProbityButton>
              <ProbityButton
                variant="danger"
                size="md"
                onClick={() => handleRevokeSession(revokeSessionId)}
                disabled={revokeSessionMutation.isPending}
              >
                Revoke
              </ProbityButton>
            </>
          }
        >
          <div className={styles.modalContent}>
            <p className={styles.modalText}>
              Are you sure you want to revoke this session? You will be logged out on that device.
            </p>
          </div>
        </Modal>
      )}
    </>
  );
}
