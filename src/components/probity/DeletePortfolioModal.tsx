import React from 'react';
import { Modal } from './Modal';
import { ProbityButton } from './ProbityButton';
import { useMutation } from '@/hooks/useMutation';
import { portfolioService } from '@/services/portfolio';
import { toast } from 'sonner';
import { AlertTriangle } from 'lucide-react';
import type { Portfolio } from '@/types/portfolio';

interface DeletePortfolioModalProps {
  open: boolean;
  onClose: () => void;
  portfolio: Portfolio | null;
  onSuccess?: () => void;
}

export const DeletePortfolioModal: React.FC<DeletePortfolioModalProps> = ({
  open,
  onClose,
  portfolio,
  onSuccess,
}) => {
  const deleteMutation = useMutation(async (id: string) => {
    return portfolioService.deletePortfolio(id);
  });

  const handleDelete = async () => {
    if (!portfolio?.id) return;

    try {
      await deleteMutation.execute(portfolio.id);
      toast.success('Portfolio deleted');
      onSuccess?.();
      onClose();
    } catch (err) {
      const error = err as { response?: { status?: number } };
      if (error.response?.status === 404) {
        toast.error('Portfolio not found');
      } else {
        toast.error('Failed to delete. Try again.');
      }
    }
  };

  const footer = (
    <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
      <ProbityButton
        variant="ghost"
        onClick={onClose}
        disabled={deleteMutation.isPending}
      >
        Cancel
      </ProbityButton>
      <ProbityButton
        variant="danger"
        onClick={handleDelete}
        loading={deleteMutation.isPending}
        disabled={deleteMutation.isPending}
      >
        Delete Portfolio
      </ProbityButton>
    </div>
  );

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Delete portfolio?"
      size="sm"
      footer={footer}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <p
          style={{
            fontFamily: 'var(--font-body)',
            fontSize: '14px',
            color: 'var(--pb-text-primary)',
            margin: 0,
          }}
        >
          <strong>{portfolio?.name}</strong> will be permanently deleted.
        </p>
        <p
          style={{
            fontFamily: 'var(--font-body)',
            fontSize: '13px',
            color: 'var(--pb-text-secondary)',
            margin: 0,
          }}
        >
          This cannot be undone.
        </p>

        <div
          style={{
            display: 'flex',
            gap: '12px',
            padding: '12px',
            border: '1px solid var(--pb-warning)',
            backgroundColor: 'rgba(245, 158, 11, 0.08)',
            borderRadius: '6px',
            alignItems: 'flex-start',
          }}
        >
          <AlertTriangle
            size={16}
            style={{ color: 'var(--pb-warning)', flexShrink: 0, marginTop: '2px' }}
          />
          <p
            style={{
              fontFamily: 'var(--font-body)',
              fontSize: '13px',
              color: 'var(--pb-text-primary)',
              margin: 0,
            }}
          >
            All positions and simulation history will also be deleted.
          </p>
        </div>
      </div>
    </Modal>
  );
};
