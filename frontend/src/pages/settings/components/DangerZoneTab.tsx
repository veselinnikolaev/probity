import React, { useState } from 'react';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { InputField } from '@/components/probity/InputField';
import { Modal } from '@/components/probity/Modal';
import { useProfile } from '@/hooks/settings';
import { useAuth } from '@/hooks/auth';
import { useNavigate } from 'react-router-dom';
import styles from '../settings.module.css';

const DELETE_CONFIRMATION_WORD = 'DELETE';

export function DangerZoneTab() {
  const { deleteAccountMutation } = useProfile();
  const { logout } = useAuth();
  const navigate = useNavigate();
  
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteConfirmInput, setDeleteConfirmInput] = useState('');

  const handleDeleteAccount = async () => {
    if (deleteConfirmInput !== DELETE_CONFIRMATION_WORD) return;
    try {
      await deleteAccountMutation.execute();
      await logout();
      navigate('/login', { replace: true });
    } catch {
      // keep modal open so user can retry
    }
  };

  return (
    <>
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
                Permanently delete your account and all associated data. This action cannot be
                undone.
              </p>
            </div>
          </div>
          <ProbityButton
            variant="danger"
            size="md"
            onClick={() => { setDeleteConfirmInput(''); setShowDeleteConfirm(true); }}
          >
            Delete Account
          </ProbityButton>
        </div>
      </div>

      {/* Delete Account Modal */}
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
              disabled={deleteConfirmInput !== DELETE_CONFIRMATION_WORD || deleteAccountMutation.isPending}
            >
              {deleteAccountMutation.isPending ? 'Deleting…' : 'Delete Account'}
            </ProbityButton>
          </>
        }
      >
        <div className={styles.modalContent}>
          <p className={styles.modalText}>
            Are you sure you want to delete your account? All your data will be permanently removed.
          </p>
          <p className={styles.modalWarning}>
            Type <span className={styles.highlightText}>{DELETE_CONFIRMATION_WORD}</span> to confirm.
          </p>
          <InputField
            placeholder={`Type ${DELETE_CONFIRMATION_WORD} to confirm`}
            value={deleteConfirmInput}
            onChange={(e) => setDeleteConfirmInput(e.target.value)}
          />
        </div>
      </Modal>
    </>
  );
}
