import React, { useState, useMemo, useEffect } from 'react';
import { AssetTag } from './AssetTag';
import { ProbityButton } from './ProbityButton';
import { portfolioService } from "../../api/portfolioService";
import { InputField } from './InputField';

interface Asset {
  id: string;
  name: string;
  ticker: string;
  type: 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND' | 'FOREX' | 'COMMODITY';
  currentPrice: number;
}

interface EditingPosition {
    ticker: string;
    name: string;
    type: 'STOCK' | 'ETF' | 'CRYPTO' | 'BOND';
    currentPrice: number;
    quantity: number;
}

interface AddPositionDrawerProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  portfolioId: string;
  editingPositionId?: string | null;
  editingPosition?: EditingPosition | null;
}

// Mock asset data for search - in a real app, this would come from an API
const MOCK_ASSETS: Asset[] = [
  { id: '1', name: 'Apple Inc.', ticker: 'AAPL', type: 'STOCK', currentPrice: 195.48 },
  { id: '2', name: 'Microsoft Corporation', ticker: 'MSFT', type: 'STOCK', currentPrice: 422.90 },
  { id: '3', name: 'Vanguard S&P 500 ETF', ticker: 'VOO', type: 'ETF', currentPrice: 481.52 },
  { id: '4', name: 'Ethereum', ticker: 'ETH', type: 'CRYPTO', currentPrice: 3840.20 },
  { id: '5', name: 'Tesla Inc.', ticker: 'TSLA', type: 'STOCK', currentPrice: 245.60 },
  { id: '6', name: 'Amazon.com Inc.', ticker: 'AMZN', type: 'STOCK', currentPrice: 189.42 },
  { id: '7', name: 'iShares Core Bond ETF', ticker: 'BND', type: 'BOND', currentPrice: 74.85 },
  { id: '8', name: 'Bitcoin', ticker: 'BTC', type: 'CRYPTO', currentPrice: 67250.00 },
  { id: '9', name: 'Google (Alphabet)', ticker: 'GOOGL', type: 'STOCK', currentPrice: 178.95 },
  { id: '10', name: 'Nvidia Corporation', ticker: 'NVDA', type: 'STOCK', currentPrice: 988.45 },
];

export function AddPositionDrawer({
                                      open,
                                      onClose,
                                      onSuccess,
                                      portfolioId,
                                      editingPositionId,
                                      editingPosition,
}: AddPositionDrawerProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAsset, setSelectedAsset] = useState<Asset | null>(null);
  const [quantity, setQuantity] = useState('');
  const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        if (!open) return;
        if (editingPosition) {
            setSelectedAsset({
                id: editingPosition.ticker,
                name: editingPosition.name,
                ticker: editingPosition.ticker,
                type: editingPosition.type,
                currentPrice: editingPosition.currentPrice,
            });
            setQuantity(editingPosition.quantity.toString());
        } else {
            setSelectedAsset(null);
            setQuantity('');
            setSearchQuery('');
        }
    }, [open, editingPosition]);

  const searchResults = useMemo(() => {
    if (!searchQuery.trim()) return [];
    const query = searchQuery.toLowerCase();
    return MOCK_ASSETS.filter(
      (asset) =>
        asset.name.toLowerCase().includes(query) ||
        asset.ticker.toLowerCase().includes(query)
    );
  }, [searchQuery]);

  const handleSelectAsset = (asset: Asset) => {
    setSelectedAsset(asset);
    setSearchQuery('');
  };

    const handleAddPosition = async () => {
        if (!selectedAsset || !quantity) return;

        setIsLoading(true);
        try {
            if (editingPositionId) {
                await portfolioService.updatePosition(portfolioId, editingPositionId, {
                    quantity: parseFloat(quantity),
                });
            } else {
                await portfolioService.addPosition(portfolioId, {
                    ticker: selectedAsset.ticker,
                    quantity: parseFloat(quantity),
                });
            }
            setSelectedAsset(null);
            setQuantity('');
            setSearchQuery('');
            onSuccess?.();
            onClose();
        } catch (error) {
            console.error('Failed to save position:', error);
        } finally {
            setIsLoading(false);
        }
    };

  if (!open) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          zIndex: 999,
          animation: 'fade-in 0.2s ease-out',
        }}
      />

      {/* Drawer */}
      <div
        style={{
          position: 'fixed',
          right: 0,
          top: 0,
          bottom: 0,
          width: '420px',
          backgroundColor: 'var(--pb-surface)',
          borderLeft: '1px solid var(--pb-border)',
          display: 'flex',
          flexDirection: 'column',
          zIndex: 1000,
          animation: 'slide-in 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
          boxShadow: '-4px 0 16px rgba(0, 0, 0, 0.3)',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid var(--pb-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexShrink: 0,
          }}
        >
          <h2
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '18px',
              fontWeight: 700,
              color: 'var(--pb-text-primary)',
              margin: 0,
            }}
          >
            {editingPositionId ? 'Edit Position' : 'Add Position'}
          </h2>
          <button
            onClick={onClose}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '32px',
              height: '32px',
              background: 'transparent',
              border: 'none',
              color: 'var(--pb-text-secondary)',
              fontSize: '20px',
              cursor: 'pointer',
              transition: 'color 0.12s',
            }}
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLElement).style.color = 'var(--pb-text-primary)';
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLElement).style.color = 'var(--pb-text-secondary)';
            }}
            title="Close drawer"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '24px',
            display: 'flex',
            flexDirection: 'column',
            gap: '20px',
          }}
        >
          {!selectedAsset ? (
            <>
              {/* Search Input */}
              <div>
                <label
                  style={{
                    display: 'block',
                    fontFamily: 'var(--font-body)',
                    fontSize: '12px',
                    fontWeight: 500,
                    color: 'var(--pb-text-secondary)',
                    marginBottom: '8px',
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                  }}
                >
                  Search Asset
                </label>
                <input
                  type="text"
                  placeholder="Search by name or ticker..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    fontFamily: 'var(--font-body)',
                    fontSize: '13px',
                    backgroundColor: 'var(--pb-surface-elevated)',
                    border: '1px solid var(--pb-border)',
                    borderRadius: '6px',
                    color: 'var(--pb-text-primary)',
                    outline: 'none',
                    transition: 'border-color 0.12s',
                    boxSizing: 'border-box',
                  }}
                  onFocus={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59, 130, 246, 0.4)';
                  }}
                  onBlur={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)';
                  }}
                />
              </div>

              {/* Search Results */}
              {searchQuery && (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                    maxHeight: '320px',
                    overflowY: 'auto',
                  }}
                >
                  {searchResults.length > 0 ? (
                    searchResults.map((asset) => (
                      <button
                        key={asset.id}
                        onClick={() => handleSelectAsset(asset)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '12px',
                          padding: '12px',
                          backgroundColor: 'var(--pb-surface-elevated)',
                          border: '1px solid var(--pb-border)',
                          borderRadius: '6px',
                          cursor: 'pointer',
                          textAlign: 'left',
                          transition: 'all 0.12s',
                        }}
                        onMouseEnter={(e) => {
                          (e.currentTarget as HTMLElement).style.backgroundColor =
                            'rgba(59, 130, 246, 0.1)';
                          (e.currentTarget as HTMLElement).style.borderColor =
                            'rgba(59, 130, 246, 0.3)';
                        }}
                        onMouseLeave={(e) => {
                          (e.currentTarget as HTMLElement).style.backgroundColor =
                            'var(--pb-surface-elevated)';
                          (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)';
                        }}
                      >
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div
                            style={{
                              fontFamily: 'var(--font-body)',
                              fontSize: '13px',
                              fontWeight: 500,
                              color: 'var(--pb-text-primary)',
                              marginBottom: '4px',
                            }}
                          >
                            {asset.name}
                          </div>
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: '8px',
                              fontSize: '12px',
                            }}
                          >
                            <span
                              style={{
                                fontFamily: 'var(--font-mono)',
                                fontWeight: 500,
                                color: 'var(--pb-text-secondary)',
                              }}
                            >
                              {asset.ticker}
                            </span>
                            <AssetTag type={asset.type} size="sm" />
                          </div>
                        </div>
                        <div
                          style={{
                            fontFamily: 'var(--font-mono)',
                            fontSize: '13px',
                            fontWeight: 500,
                            color: 'var(--pb-accent)',
                            flexShrink: 0,
                          }}
                        >
                          ${asset.currentPrice.toFixed(2)}
                        </div>
                      </button>
                    ))
                  ) : (
                    <div
                      style={{
                        textAlign: 'center',
                        padding: '32px 16px',
                        color: 'var(--pb-text-secondary)',
                        fontSize: '14px',
                      }}
                    >
                      No assets found
                    </div>
                  )}
                </div>
              )}

              {/* Empty State */}
              {!searchQuery && (
                <div
                  style={{
                    textAlign: 'center',
                    padding: '48px 16px',
                    color: 'var(--pb-text-secondary)',
                  }}
                >
                  <div style={{ fontSize: '32px', marginBottom: '12px', opacity: 0.6 }}>🔍</div>
                  <p style={{ margin: 0, fontSize: '14px' }}>
                    Search for an asset to add to your portfolio
                  </p>
                </div>
              )}
            </>
          ) : (
            <>
              {/* Selected Asset */}
              <div
                style={{
                  padding: '12px',
                  backgroundColor: 'rgba(59, 130, 246, 0.05)',
                  border: '1px solid rgba(59, 130, 246, 0.2)',
                  borderRadius: '6px',
                }}
              >
                <div
                  style={{
                    fontFamily: 'var(--font-body)',
                    fontSize: '13px',
                    fontWeight: 500,
                    color: 'var(--pb-text-primary)',
                    marginBottom: '4px',
                  }}
                >
                  {selectedAsset.name}
                </div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '12px',
                    }}
                  >
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontWeight: 500,
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      {selectedAsset.ticker}
                    </span>
                    <AssetTag type={selectedAsset.type} size="sm" />
                  </div>
                  <button
                    onClick={() => {
                      setSelectedAsset(null);
                      setQuantity('');
                    }}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'var(--pb-text-secondary)',
                      cursor: 'pointer',
                      fontSize: '14px',
                    }}
                    title="Change asset"
                  >
                    ✕
                  </button>
                </div>
              </div>

              {/* Asset Details */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: '12px',
                }}
              >
                <div
                  style={{
                    padding: '12px',
                    backgroundColor: 'var(--pb-surface-elevated)',
                    borderRadius: '6px',
                  }}
                >
                  <div
                    style={{
                      fontSize: '11px',
                      color: 'var(--pb-text-secondary)',
                      marginBottom: '4px',
                      textTransform: 'uppercase',
                      fontWeight: 500,
                      letterSpacing: '0.06em',
                    }}
                  >
                    Current Price
                  </div>
                  <div
                    style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: '16px',
                      fontWeight: 600,
                      color: 'var(--pb-accent)',
                    }}
                  >
                    ${selectedAsset.currentPrice.toFixed(2)}
                  </div>
                </div>
                <div
                  style={{
                    padding: '12px',
                    backgroundColor: 'var(--pb-surface-elevated)',
                    borderRadius: '6px',
                  }}
                >
                  <div
                    style={{
                      fontSize: '11px',
                      color: 'var(--pb-text-secondary)',
                      marginBottom: '4px',
                      textTransform: 'uppercase',
                      fontWeight: 500,
                      letterSpacing: '0.06em',
                    }}
                  >
                    Type
                  </div>
                  <AssetTag type={selectedAsset.type} size="md" />
                </div>
              </div>

              {/* Quantity Input */}
              <div>
                <label
                  style={{
                    display: 'block',
                    fontFamily: 'var(--font-body)',
                    fontSize: '12px',
                    fontWeight: 500,
                    color: 'var(--pb-text-secondary)',
                    marginBottom: '8px',
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                  }}
                >
                  Quantity
                </label>
                <input
                  type="number"
                  placeholder="Enter quantity..."
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  step="0.01"
                  min="0"
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    fontFamily: 'var(--font-mono)',
                    fontSize: '13px',
                    backgroundColor: 'var(--pb-surface-elevated)',
                    border: '1px solid var(--pb-border)',
                    borderRadius: '6px',
                    color: 'var(--pb-text-primary)',
                    outline: 'none',
                    transition: 'border-color 0.12s',
                    boxSizing: 'border-box',
                  }}
                  onFocus={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59, 130, 246, 0.4)';
                  }}
                  onBlur={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)';
                  }}
                />
              </div>

              {/* Summary */}
              {quantity && (
                <div
                  style={{
                    padding: '12px',
                    backgroundColor: 'rgba(16, 185, 129, 0.05)',
                    border: '1px solid rgba(16, 185, 129, 0.2)',
                    borderRadius: '6px',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: '6px',
                    }}
                  >
                    <span
                      style={{
                        fontFamily: 'var(--font-body)',
                        fontSize: '13px',
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      Quantity
                    </span>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        fontWeight: 500,
                        color: 'var(--pb-text-primary)',
                      }}
                    >
                      {parseFloat(quantity).toLocaleString()}
                    </span>
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                    }}
                  >
                    <span
                      style={{
                        fontFamily: 'var(--font-body)',
                        fontSize: '13px',
                        color: 'var(--pb-text-secondary)',
                      }}
                    >
                      Total Value
                    </span>
                    <span
                      style={{
                        fontFamily: 'var(--font-mono)',
                        fontSize: '13px',
                        fontWeight: 600,
                        color: '#10B981',
                      }}
                    >
                      ${(parseFloat(quantity) * selectedAsset.currentPrice).toFixed(2)}
                    </span>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '16px 24px',
            borderTop: '1px solid var(--pb-border)',
            display: 'flex',
            gap: '12px',
            flexShrink: 0,
          }}
        >
          <ProbityButton
            variant="ghost"
            size="md"
            onClick={onClose}
            style={{ flex: 1 }}
          >
            Cancel
          </ProbityButton>
          <ProbityButton
            variant="primary"
            size="md"
            onClick={handleAddPosition}
            disabled={!selectedAsset || !quantity}
            loading={isLoading}
            style={{ flex: 1 }}
          >
            {editingPositionId ? 'Update' : 'Add to Portfolio'}
          </ProbityButton>
        </div>

        {/* Animations */}
        <style>{`
          @keyframes slide-in {
            from {
              transform: translateX(100%);
              opacity: 0;
            }
            to {
              transform: translateX(0);
              opacity: 1;
            }
          }
          @keyframes fade-in {
            from {
              opacity: 0;
            }
            to {
              opacity: 1;
            }
          }
        `}</style>
      </div>
    </>
  );
}
