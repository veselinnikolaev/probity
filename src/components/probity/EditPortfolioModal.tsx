import React, { useState, useEffect } from 'react';
import { Modal } from './Modal';
import { InputField } from './InputField';
import { ProbityButton } from './ProbityButton';
import { useMutation } from '@/hooks/useMutation';
import { portfolioService } from '@/services/portfolio';
import { toast } from 'sonner';
import type { Portfolio } from '@/types/portfolio';

interface EditPortfolioModalProps {
  open: boolean;
  onClose: () => void;
  portfolio: Portfolio | null;
  onSuccess?: () => void;
}

export const EditPortfolioModal: React.FC<EditPortfolioModalProps> = ({
  open,
  onClose,
  portfolio,
  onSuccess,
}) => {
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [nameError, setNameError] = useState('');
  const [descriptionError, setDescriptionError] = useState('');

  const updateMutation = useMutation(
    async (data: { name: string; description?: string }) => {
      if (!portfolio?.id) throw new Error('Portfolio ID is required');
      return portfolioService.updatePortfolio(portfolio.id, data);
    }
  );

  useEffect(() => {
    if (portfolio) {
      setFormData({
        name: portfolio.name,
        description: portfolio.description || '',
      });
      setNameError('');
      setDescriptionError('');
    }
  }, [portfolio]);

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, name: e.target.value }));
    setNameError('');
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, description: e.target.value }));
    setDescriptionError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      setNameError('Portfolio name is required');
      return;
    }

    try {
      await updateMutation.execute({
        name: formData.name,
        description: formData.description || undefined,
      });
      toast.success('Portfolio updated');
      onSuccess?.();
      onClose();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to update portfolio';
      if (errorMessage.toLowerCase().includes('name')) {
        setNameError(errorMessage);
      } else if (errorMessage.toLowerCase().includes('description')) {
        setDescriptionError(errorMessage);
      }
    }
  };

  const handleClose = () => {
    setFormData({ name: '', description: '' });
    setNameError('');
    setDescriptionError('');
    updateMutation.reset();
    onClose();
  };

  const footer = (
    <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
      <ProbityButton
        variant="ghost"
        onClick={handleClose}
        disabled={updateMutation.isPending}
      >
        Cancel
      </ProbityButton>
      <ProbityButton
        variant="primary"
        onClick={handleSubmit}
        loading={updateMutation.isPending}
      >
        Save Changes
      </ProbityButton>
    </div>
  );

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Edit Portfolio"
      subtitle="Update portfolio details"
      size="md"
      footer={footer}
    >
      <form onSubmit={handleSubmit}>
        <InputField
          label="Portfolio Name"
          value={formData.name}
          onChange={handleNameChange}
          errorMessage={nameError}
          inputState={nameError ? 'error' : 'default'}
          disabled={updateMutation.isPending}
          required
        />

        <InputField
          label="Description"
          placeholder="Describe your portfolio strategy…"
          value={formData.description}
          onChange={handleDescriptionChange}
          errorMessage={descriptionError}
          inputState={descriptionError ? 'error' : 'default'}
          disabled={updateMutation.isPending}
        />
      </form>
    </Modal>
  );
};
