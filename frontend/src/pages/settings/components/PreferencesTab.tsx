import React, { useState, useEffect } from 'react';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { DropdownSelect } from '@/components/probity/DropdownSelect';
import { usePreferences } from '@/hooks/settings';
import styles from '../settings.module.css';
import { CURRENCIES, VAR_CONFIDENCE_LEVELS, TIME_HORIZONS } from '@/constants/preferences';

export function PreferencesTab() {
  const { preferences, updatePreferencesMutation } = usePreferences();
  const [preferencesSuccess, setPreferencesSuccess] = useState<string | null>(null);
  const [preferencesForm, setPreferencesForm] = useState<{
    defaultCurrency: string;
    defaultConfidenceLevel: string;
    defaultTimeHorizon: string;
  }>({
    defaultCurrency: CURRENCIES[0].value,
    defaultConfidenceLevel: VAR_CONFIDENCE_LEVELS[1].value,
    defaultTimeHorizon: TIME_HORIZONS[0].value,
  });

  // Sync preferences form once data arrives
  useEffect(() => {
    if (preferences.data) {
      setPreferencesForm({
        defaultCurrency: preferences.data.defaultCurrency,
        defaultConfidenceLevel: String(preferences.data.defaultConfidenceLevel),
        defaultTimeHorizon: preferences.data.defaultTimeHorizon,
      });
    }
  }, [preferences.data]);

  const handleSavePreferences = async () => {
    setPreferencesSuccess(null);
    try {
      await updatePreferencesMutation.execute({
        defaultCurrency: preferencesForm.defaultCurrency,
        defaultConfidenceLevel: Number(preferencesForm.defaultConfidenceLevel),
        defaultTimeHorizon: preferencesForm.defaultTimeHorizon,
      });
      setPreferencesSuccess('Preferences saved');
      setTimeout(() => setPreferencesSuccess(null), 3000);
    } catch {
      // surface error inline if needed
    }
  };

  return (
    <div className={styles.tabContent}>
      <div className={styles.contentHeader}>
        <h1 className={styles.contentTitle}>Preferences</h1>
        <p className={styles.contentSubtitle}>Customize your default settings</p>
      </div>

      {preferences.isLoading ? (
        <p className={styles.loadingText}>Loading…</p>
      ) : (
        <div className={styles.preferencesForm}>
          {preferencesSuccess && <p className={styles.successText}>{preferencesSuccess}</p>}

          <DropdownSelect
            label="Default Currency"
            options={CURRENCIES}
            value={preferencesForm.defaultCurrency}
            onChange={(value) =>
              setPreferencesForm({ ...preferencesForm, defaultCurrency: value })
            }
          />
          <DropdownSelect
            label="Default Confidence Level for VaR"
            options={VAR_CONFIDENCE_LEVELS}
            value={preferencesForm.defaultConfidenceLevel}
            onChange={(value) =>
              setPreferencesForm({ ...preferencesForm, defaultConfidenceLevel: value })
            }
          />
          <DropdownSelect
            label="Default Time Horizon"
            options={TIME_HORIZONS}
            value={preferencesForm.defaultTimeHorizon}
            onChange={(value) =>
              setPreferencesForm({ ...preferencesForm, defaultTimeHorizon: value })
            }
          />
          <ProbityButton
            variant="primary"
            size="md"
            onClick={handleSavePreferences}
            disabled={updatePreferencesMutation.isPending}
          >
            {updatePreferencesMutation.isPending ? 'Saving…' : 'Save Preferences'}
          </ProbityButton>
        </div>
      )}
    </div>
  );
}
