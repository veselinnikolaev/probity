import React, { useState } from 'react';
import { ProfileTab } from './components/ProfileTab';
import { SecurityTab } from './components/SecurityTab';
import { PreferencesTab } from './components/PreferencesTab';
import { ApiKeysTab } from './components/ApiKeysTab';
import { DangerZoneTab } from './components/DangerZoneTab';
import styles from './settings.module.css';

type TabType = 'profile' | 'security' | 'preferences' | 'api-keys' | 'danger-zone';

const SettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('profile');

  return (
    <div className={styles.settingsContainer}>
      {/* Left sidebar navigation */}
      <nav className={styles.settingsNav}>
        <div className={styles.navHeader}>
          <h2 className={styles.navTitle}>Settings</h2>
        </div>
        <div className={styles.navItems}>
          {(
            [
              { id: 'profile', icon: '👤', label: 'Profile' },
              { id: 'security', icon: '🔒', label: 'Security' },
              { id: 'preferences', icon: '⚙️', label: 'Preferences' },
              { id: 'api-keys', icon: '🔑', label: 'API Keys' },
              { id: 'danger-zone', icon: '⚠️', label: 'Danger Zone', danger: true },
            ] as { id: TabType; icon: string; label: string; danger?: boolean }[]
          ).map(({ id, icon, label, danger }) => (
            <button
              key={id}
              className={[
                styles.navItem,
                danger ? styles.navItemDanger : '',
                activeTab === id ? styles.navItemActive : '',
              ]
                .filter(Boolean)
                .join(' ')}
              onClick={() => setActiveTab(id)}
            >
              <span className={styles.navItemIcon}>{icon}</span>
              <span>{label}</span>
            </button>
          ))}
        </div>
      </nav>

      {/* Content area */}
      <main className={styles.settingsContent}>
        {activeTab === 'profile' && <ProfileTab />}
        {activeTab === 'security' && <SecurityTab />}
        {activeTab === 'preferences' && <PreferencesTab />}
        {activeTab === 'api-keys' && <ApiKeysTab />}
        {activeTab === 'danger-zone' && <DangerZoneTab />}
      </main>
    </div>
  );
};

export default SettingsPage;