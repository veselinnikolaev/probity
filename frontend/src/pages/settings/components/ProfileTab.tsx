import React, { useState, useEffect } from 'react';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { InputField } from '@/components/probity/InputField';
import { useProfile } from '@/hooks/settings';
import styles from '../settings.module.css';

export function ProfileTab() {
  const { profile, updateProfileMutation } = useProfile();
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileForm, setProfileForm] = useState({ firstName: '', lastName: '' });

  // Sync form state once profile data arrives
  useEffect(() => {
    if (profile.data) {
      setProfileForm({
        firstName: profile.data.firstName ?? '',
        lastName: profile.data.lastName ?? '',
      });
    }
  }, [profile.data]);

  const handleSaveProfile = async () => {
    setProfileError(null);
    try {
      await updateProfileMutation.execute({
        firstName: profileForm.firstName,
        lastName: profileForm.lastName,
      });
      setIsEditingProfile(false);
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : 'Failed to update profile');
    }
  };

  // Derived display values
  const displayName = profile.data
    ? profile.data.firstName && profile.data.lastName
      ? `${profile.data.firstName} ${profile.data.lastName}`
      : profile.data.username
    : '…';

  const userInitials = profile.data
    ? ((profile.data.firstName?.[0] ?? '') + (profile.data.lastName?.[0] ?? '')).toUpperCase() ||
    profile.data.username?.[0]?.toUpperCase() ||
    'U'
    : 'U';

  const memberSince = profile.data?.memberSince
    ? new Date(profile.data.memberSince).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
    : '…';

  return (
    <div className={styles.tabContent}>
      <div className={styles.contentHeader}>
        <h1 className={styles.contentTitle}>Profile</h1>
        <p className={styles.contentSubtitle}>Manage your profile information</p>
      </div>

      {profile.isLoading ? (
        <p className={styles.loadingText}>Loading…</p>
      ) : !isEditingProfile ? (
        <div className={styles.profileDisplay}>
          <div className={styles.avatarSection}>
            <div className={styles.avatarCircle}>{userInitials}</div>
            <div className={styles.avatarInfo}>
              <p className={styles.avatarName}>{displayName}</p>
              <p className={styles.avatarEmail}>{profile.data?.email}</p>
            </div>
          </div>
          <div className={styles.memberSection}>
            <p className={styles.memberLabel}>Member since</p>
            <p className={styles.memberDate}>{memberSince}</p>
          </div>
          <ProbityButton variant="secondary" size="md" onClick={() => setIsEditingProfile(true)}>
            Edit Profile
          </ProbityButton>
        </div>
      ) : (
        <div className={styles.profileForm}>
          {profileError && <p className={styles.errorText}>{profileError}</p>}
          <InputField
            label="First Name"
            value={profileForm.firstName}
            onChange={(e) => setProfileForm({ ...profileForm, firstName: e.target.value })}
          />
          <InputField
            label="Last Name"
            value={profileForm.lastName}
            onChange={(e) => setProfileForm({ ...profileForm, lastName: e.target.value })}
          />
          <InputField
            label="Email"
            type="email"
            value={profile.data?.email ?? ''}
            disabled
          />
          <div className={styles.formActions}>
            <ProbityButton
              variant="primary"
              size="md"
              onClick={handleSaveProfile}
              disabled={updateProfileMutation.isPending}
            >
              {updateProfileMutation.isPending ? 'Saving…' : 'Save Changes'}
            </ProbityButton>
            <ProbityButton
              variant="secondary"
              size="md"
              onClick={() => { setIsEditingProfile(false); setProfileError(null); }}
            >
              Cancel
            </ProbityButton>
          </div>
        </div>
      )}
    </div>
  );
}
