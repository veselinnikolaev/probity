import React, { useState } from 'react';
import { Modal } from './Modal';
import { InputField } from './InputField';
import { ProbityButton } from './ProbityButton';

interface CreatePortfolioModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { name: string; description?: string }) => Promise<void>;
  loading?: boolean;
}

export const CreatePortfolioModal: React.FC<CreatePortfolioModalProps> = ({
  open,
  onClose,
  onSubmit,
  loading = false,
}) => {
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [error, setError] = useState('');

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, name: e.target.value }));
    setError('');
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, description: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      setError('Portfolio name is required');
      return;
    }

    try {
      await onSubmit({
        name: formData.name,
        description: formData.description || undefined,
      });
      setFormData({ name: '', description: '' });
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create portfolio');
    }
  };

  const handleClose = () => {
    setFormData({ name: '', description: '' });
    setError('');
    onClose();
  };

  const footer = (
    <div className="create-portfolio-modal__footer">
      <ProbityButton
        variant="ghost"
        onClick={handleClose}
        disabled={loading}
      >
        Cancel
      </ProbityButton>
      <ProbityButton
        variant="primary"
        onClick={handleSubmit}
        loading={loading}
      >
        Create Portfolio
      </ProbityButton>
    </div>
  );

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Create New Portfolio"
      subtitle="Set up a new portfolio to manage your investments"
      size="md"
      footer={footer}
    >
      <form className="create-portfolio-modal__form" onSubmit={handleSubmit}>
        <InputField
          label="Portfolio Name"
          placeholder="e.g., Growth Portfolio"
          value={formData.name}
          onChange={handleNameChange}
          errorMessage={error}
          inputState={error ? 'error' : 'default'}
          disabled={loading}
        />

        <InputField
          label="Description (Optional)"
          placeholder="Add a description for your portfolio"
          value={formData.description}
          onChange={handleDescriptionChange}
          disabled={loading}
        />
      </form>
    </Modal>
  );
};
