import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { ProbityButton } from '../../components/probity/ProbityButton';
import { InputField } from '../../components/probity/InputField';
import { DropdownSelect } from '../../components/probity/DropdownSelect';
import { Modal } from '../../components/probity/Modal';
import styles from './settings.module.css';

type TabType = 'profile' | 'security' | 'preferences' | 'api-keys' | 'danger-zone';

interface ProfileData {
  firstName: string;
  lastName: string;
}

interface PasswordData {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

interface Session {
  id: string;
  device: string;
  location: string;
  lastActive: string;
}

interface PreferencesData {
  defaultCurrency: string;
  defaultConfidenceLevel: string;
  defaultTimeHorizon: string;
}

const SettingsPage: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<TabType>('profile');
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showPasswordChange, setShowPasswordChange] = useState(false);
  const [revokeSessionId, setRevokeSessionId] = useState<string | null>(null);

  // Profile state
  const [profileData, setProfileData] = useState<ProfileData>({
    firstName: (user as any)?.firstName || '',
    lastName: (user as any)?.lastName || '',
  });

  // Password state
  const [passwordData, setPasswordData] = useState<PasswordData>({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  // Preferences state
  const [preferencesData, setPreferencesData] = useState<PreferencesData>({
    defaultCurrency: 'usd',
    defaultConfidenceLevel: '95',
    defaultTimeHorizon: '1d',
  });

  // Mock sessions (would come from backend)
  const [activeSessions] = useState<Session[]>([]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const handleDeleteAccount = async () => {
    // TODO: Call backend delete account endpoint
    // Then logout and redirect to login
    setShowDeleteConfirm(false);
    handleLogout();
  };

  const handleChangePassword = async () => {
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      alert('New passwords do not match');
      return;
    }
    // TODO: Call backend change password endpoint
    setShowPasswordChange(false);
    setPasswordData({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
  };

  const handleSaveProfile = async () => {
    // TODO: Call backend update profile endpoint
    setIsEditingProfile(false);
  };

  const handleRevokeSession = async (sessionId: string) => {
    // TODO: Call backend revoke session endpoint
    setRevokeSessionId(null);
  };

  const handleSavePreferences = async () => {
    // TODO: Call backend update preferences endpoint
  };

  const memberSince = (user as any)?.createdAt
    ? new Date((user as any).createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : 'Unknown';

  const userInitials = ((user as any)?.firstName?.[0] || '') + ((user as any)?.lastName?.[0] || '') || user?.username?.[0]?.toUpperCase() || 'U';

  return (
    <div className={styles.settingsContainer}>
      {/* Left sidebar navigation */}
      <nav className={styles.settingsNav}>
        <div className={styles.navHeader}>
          <h2 className={styles.navTitle}>Settings</h2>
        </div>

        <div className={styles.navItems}>
          <button
            className={`${styles.navItem} ${activeTab === 'profile' ? styles.navItemActive : ''}`}
            onClick={() => setActiveTab('profile')}
          >
            <span className={styles.navItemIcon}>👤</span>
            <span>Profile</span>
          </button>
          <button
            className={`${styles.navItem} ${activeTab === 'security' ? styles.navItemActive : ''}`}
            onClick={() => setActiveTab('security')}
          >
            <span className={styles.navItemIcon}>🔒</span>
            <span>Security</span>
          </button>
          <button
            className={`${styles.navItem} ${activeTab === 'preferences' ? styles.navItemActive : ''}`}
            onClick={() => setActiveTab('preferences')}
          >
            <span className={styles.navItemIcon}>⚙️</span>
            <span>Preferences</span>
          </button>
          <button
            className={`${styles.navItem} ${activeTab === 'api-keys' ? styles.navItemActive : ''}`}
            onClick={() => setActiveTab('api-keys')}
          >
            <span className={styles.navItemIcon}>🔑</span>
            <span>API Keys</span>
          </button>
          <button
            className={`${styles.navItem} ${styles.navItemDanger} ${
              activeTab === 'danger-zone' ? styles.navItemActive : ''
            }`}
            onClick={() => setActiveTab('danger-zone')}
          >
            <span className={styles.navItemIcon}>⚠️</span>
            <span>Danger Zone</span>
          </button>
        </div>
      </nav>

      {/* Content area */}
      <main className={styles.settingsContent}>
        {/* Profile Tab */}
        {activeTab === 'profile' && (
          <div className={styles.tabContent}>
            <div className={styles.contentHeader}>
              <h1 className={styles.contentTitle}>Profile</h1>
              <p className={styles.contentSubtitle}>Manage your profile information</p>
            </div>

            {!isEditingProfile ? (
              <div className={styles.profileDisplay}>
                <div className={styles.avatarSection}>
                  <div className={styles.avatarCircle}>{userInitials}</div>
                  <div className={styles.avatarInfo}>
                    <p className={styles.avatarName}>
                      {(user as any)?.firstName && (user as any)?.lastName
                        ? `${(user as any).firstName} ${(user as any).lastName}`
                        : user?.username}
                    </p>
                    <p className={styles.avatarEmail}>{(user as any)?.email || user?.role}</p>
                  </div>
                </div>

                <div className={styles.memberSection}>
                  <p className={styles.memberLabel}>Member since</p>
                  <p className={styles.memberDate}>{memberSince}</p>
                </div>

                <ProbityButton
                  variant="secondary"
                  size="md"
                  onClick={() => setIsEditingProfile(true)}
                >
                  Edit Profile
                </ProbityButton>
              </div>
            ) : (
              <div className={styles.profileForm}>
                <InputField
                  label="First Name"
                  value={profileData.firstName}
                  onChange={(e) =>
                    setProfileData({ ...profileData, firstName: e.target.value })
                  }
                />
                <InputField
                  label="Last Name"
                  value={profileData.lastName}
                  onChange={(e) =>
                    setProfileData({ ...profileData, lastName: e.target.value })
                  }
                />
                <InputField
                  label="Email"
                  type="email"
                  value={(user as any)?.email || user?.username || ''}
                  disabled
                />

                <div className={styles.formActions}>
                  <ProbityButton
                    variant="primary"
                    size="md"
                    onClick={handleSaveProfile}
                  >
                    Save Changes
                  </ProbityButton>
                  <ProbityButton
                    variant="secondary"
                    size="md"
                    onClick={() => setIsEditingProfile(false)}
                  >
                    Cancel
                  </ProbityButton>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Security Tab */}
        {activeTab === 'security' && (
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

              {!showPasswordChange ? (
                <ProbityButton
                  variant="secondary"
                  size="md"
                  onClick={() => setShowPasswordChange(true)}
                >
                  Change Password
                </ProbityButton>
              ) : (
                <div className={styles.passwordForm}>
                  <InputField
                    label="Current Password"
                    type="password"
                    value={passwordData.currentPassword}
                    onChange={(e) =>
                      setPasswordData({
                        ...passwordData,
                        currentPassword: e.target.value,
                      })
                    }
                    placeholder="Enter current password"
                  />
                  <InputField
                    label="New Password"
                    type="password"
                    value={passwordData.newPassword}
                    onChange={(e) =>
                      setPasswordData({
                        ...passwordData,
                        newPassword: e.target.value,
                      })
                    }
                    placeholder="Enter new password"
                  />
                  <InputField
                    label="Confirm Password"
                    type="password"
                    value={passwordData.confirmPassword}
                    onChange={(e) =>
                      setPasswordData({
                        ...passwordData,
                        confirmPassword: e.target.value,
                      })
                    }
                    placeholder="Confirm new password"
                  />

                  <div className={styles.formActions}>
                    <ProbityButton
                      variant="primary"
                      size="md"
                      onClick={handleChangePassword}
                    >
                      Update Password
                    </ProbityButton>
                    <ProbityButton
                      variant="secondary"
                      size="md"
                      onClick={() => setShowPasswordChange(false)}
                    >
                      Cancel
                    </ProbityButton>
                  </div>
                </div>
              )}
            </div>

            <div className={styles.divider}></div>

            <div className={styles.securitySection}>
              <div className={styles.sectionTitle}>Active Sessions</div>
              <p className={styles.sectionDescription}>
                Manage your active sessions across devices
              </p>

              {activeSessions.length === 0 ? (
                <div className={styles.noSessions}>
                  <p className={styles.noSessionsText}>No other active sessions</p>
                </div>
              ) : (
                <div className={styles.sessionsList}>
                  {activeSessions.map((session) => (
                    <div key={session.id} className={styles.sessionItem}>
                      <div className={styles.sessionInfo}>
                        <p className={styles.sessionDevice}>{session.device}</p>
                        <p className={styles.sessionLocation}>{session.location}</p>
                        <p className={styles.sessionLastActive}>
                          Last active: {session.lastActive}
                        </p>
                      </div>
                      <ProbityButton
                        variant="secondary"
                        size="sm"
                        onClick={() => setRevokeSessionId(session.id)}
                      >
                        Revoke
                      </ProbityButton>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Preferences Tab */}
        {activeTab === 'preferences' && (
          <div className={styles.tabContent}>
            <div className={styles.contentHeader}>
              <h1 className={styles.contentTitle}>Preferences</h1>
              <p className={styles.contentSubtitle}>Customize your default settings</p>
            </div>

            <div className={styles.preferencesForm}>
              <DropdownSelect
                label="Default Currency"
                options={[
                  { value: 'usd', label: 'USD', description: 'US Dollar' },
                  { value: 'eur', label: 'EUR', description: 'Euro' },
                ]}
                value={preferencesData.defaultCurrency}
                onChange={(value) =>
                  setPreferencesData({ ...preferencesData, defaultCurrency: value })
                }
              />

              <DropdownSelect
                label="Default Confidence Level for VaR"
                options={[
                  { value: '90', label: '90%', description: 'Lower confidence' },
                  { value: '95', label: '95%', description: 'Standard confidence' },
                  { value: '99', label: '99%', description: 'Higher confidence' },
                ]}
                value={preferencesData.defaultConfidenceLevel}
                onChange={(value) =>
                  setPreferencesData({
                    ...preferencesData,
                    defaultConfidenceLevel: value,
                  })
                }
              />

              <DropdownSelect
                label="Default Time Horizon"
                options={[
                  { value: '1d', label: '1 Day' },
                  { value: '1w', label: '1 Week' },
                  { value: '1m', label: '1 Month' },
                  { value: '1y', label: '1 Year' },
                ]}
                value={preferencesData.defaultTimeHorizon}
                onChange={(value) =>
                  setPreferencesData({
                    ...preferencesData,
                    defaultTimeHorizon: value,
                  })
                }
              />

              <ProbityButton
                variant="primary"
                size="md"
                onClick={handleSavePreferences}
              >
                Save Preferences
              </ProbityButton>
            </div>
          </div>
        )}

        {/* API Keys Tab */}
        {activeTab === 'api-keys' && (
          <div className={styles.tabContent}>
            <div className={styles.contentHeader}>
              <h1 className={styles.contentTitle}>API Keys</h1>
              <p className={styles.contentSubtitle}>Manage your API keys for integrations</p>
            </div>

            <div className={styles.apiKeysSection}>
              <p className={styles.apiKeysPlaceholder}>
                API keys will be displayed here. Create a new key to get started.
              </p>
              <ProbityButton variant="primary" size="md">
                Generate API Key
              </ProbityButton>
            </div>
          </div>
        )}

        {/* Danger Zone Tab */}
        {activeTab === 'danger-zone' && (
          <div className={styles.tabContent}>
            <div className={styles.contentHeader}>
              <h1 className={styles.contentTitle}>Danger Zone</h1>
              <p className={styles.contentSubtitle}>Irreversible actions</p>
            </div>

            <div className={styles.dangerZoneCard}>
              <div className={styles.dangerZoneHeader}>
                <span className={styles.warningIcon}>⚠️</span>
                <div>
                  <h3 className={styles.dangerZoneTitle}>Delete Account</h3>
                  <p className={styles.dangerZoneDescription}>
                    Permanently delete your account and all associated data. This action cannot be undone.
                  </p>
                </div>
              </div>
              <ProbityButton
                variant="danger"
                size="md"
                onClick={() => setShowDeleteConfirm(true)}
              >
                Delete Account
              </ProbityButton>
            </div>
          </div>
        )}
      </main>

      {/* Delete Account Confirmation Modal */}
      <Modal
        open={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        title="Delete Account"
        subtitle="This action cannot be undone"
        size="md"
        footer={
          <>
            <ProbityButton
              variant="secondary"
              size="md"
              onClick={() => setShowDeleteConfirm(false)}
            >
              Cancel
            </ProbityButton>
            <ProbityButton
              variant="danger"
              size="md"
              onClick={handleDeleteAccount}
            >
              Delete Account
            </ProbityButton>
          </>
        }
      >
        <div className={styles.modalContent}>
          <p className={styles.modalText}>
            Are you sure you want to delete your account? All your data will be permanently removed.
          </p>
          <p className={styles.modalWarning}>
            Type <span className={styles.highlightText}>DELETE</span> to confirm.
          </p>
          <InputField placeholder="Type DELETE to confirm" />
        </div>
      </Modal>

      {/* Revoke Session Confirmation Modal */}
      {revokeSessionId && (
        <Modal
          open={!!revokeSessionId}
          onClose={() => setRevokeSessionId(null)}
          title="Revoke Session"
          size="sm"
          footer={
            <>
              <ProbityButton
                variant="secondary"
                size="md"
                onClick={() => setRevokeSessionId(null)}
              >
                Cancel
              </ProbityButton>
              <ProbityButton
                variant="danger"
                size="md"
                onClick={() => handleRevokeSession(revokeSessionId)}
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
    </div>
  );
};

export default SettingsPage;
