import React from 'react';
import { ProbityButton } from '@/components/probity/ProbityButton';
import styles from '../settings.module.css';

export function ApiKeysTab() {
  return (
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
  );
}
