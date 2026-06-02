import React, { useState, useEffect, useRef } from 'react';
import { AssetTag } from '@/components/probity/AssetTag';
import { ProbityButton } from '@/components/probity/ProbityButton';
import { portfolioService } from '@/services/portfolio';
import type { AssetSearchResult } from '@/types/portfolio';
import { useMutation } from '@/hooks/useMutation';
import styles from './add-position-drawer.module.css';

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

// ── Reusable label style ──────────────────────────────────────────────────────
const labelStyle: React.CSSProperties = {
    display: 'block',
    fontFamily: 'var(--font-body)',
    fontSize: 12,
    fontWeight: 500,
    color: 'var(--pb-text-secondary)',
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
};

const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '10px 12px',
    fontFamily: 'var(--font-body)',
    fontSize: 13,
    backgroundColor: 'var(--pb-surface-elevated)',
    border: '1px solid var(--pb-border)',
    borderRadius: 6,
    color: 'var(--pb-text-primary)',
    outline: 'none',
    transition: 'border-color 0.12s',
    boxSizing: 'border-box' as const,
};

const inputFocusStyle: React.CSSProperties = {
    borderColor: 'rgba(59,130,246,0.4)',
};

const inputBlurStyle: React.CSSProperties = {
    borderColor: 'var(--pb-border)',
};

export function AddPositionDrawer({
                                      open,
                                      onClose,
                                      onSuccess,
                                      portfolioId,
                                      editingPositionId,
                                      editingPosition,
                                  }: AddPositionDrawerProps) {
    const [searchQuery, setSearchQuery]         = useState('');
    const [searchResults, setSearchResults]     = useState<AssetSearchResult[]>([]);
    const [isSearching, setIsSearching]         = useState(false);
    const [selectedAsset, setSelectedAsset]     = useState<AssetSearchResult | null>(null);
    const [quantity, setQuantity]               = useState('');
    const [error, setError]                     = useState<string | null>(null);
    const debounceRef                           = useRef<ReturnType<typeof setTimeout> | null>(null);

    const submitMutation = useMutation(async (params: { isEdit: boolean; qty: number }) => {
        if (params.isEdit && editingPositionId) {
            await portfolioService.updatePosition(portfolioId, editingPositionId, { quantity: params.qty });
        } else if (selectedAsset) {
            await portfolioService.addPosition(portfolioId, {
                ticker: selectedAsset.ticker,
                quantity: params.qty,
            });
        }
    });

    // ── Seed form when editing ────────────────────────────────────────────────
    useEffect(() => {
        if (!open) return;
        if (editingPosition) {
            setSelectedAsset({
                ticker: editingPosition.ticker,
                name: editingPosition.name,
                type: editingPosition.type,
                currentPrice: editingPosition.currentPrice,
                sector: '',
            });
            setQuantity(String(editingPosition.quantity));
        } else {
            setSelectedAsset(null);
            setQuantity('');
            setSearchQuery('');
            setSearchResults([]);
        }
        setError(null);
    }, [open, editingPosition]);

    // ── Debounced asset search ────────────────────────────────────────────────
    useEffect(() => {
        if (selectedAsset) return; // don't search once asset is picked

        if (debounceRef.current) clearTimeout(debounceRef.current);

        if (!searchQuery.trim()) {
            setSearchResults([]);
            return;
        }

        debounceRef.current = setTimeout(async () => {
            setIsSearching(true);
            try {
                const results = await portfolioService.searchAssets(searchQuery);
                setSearchResults(results);
            } catch {
                setSearchResults([]);
            } finally {
                setIsSearching(false);
            }
        }, 300);

        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchQuery, selectedAsset]);

    // ── Submit ────────────────────────────────────────────────────────────────
    const handleSubmit = async () => {
        if (!selectedAsset || !quantity) return;
        const qty = parseFloat(quantity);
        if (isNaN(qty) || qty <= 0) {
            setError('Quantity must be a positive number.');
            return;
        }

        setError(null);
        try {
            await submitMutation.execute({ isEdit: !!editingPositionId, qty });
            setSelectedAsset(null);
            setQuantity('');
            setSearchQuery('');
            setSearchResults([]);
            onSuccess?.();
            onClose();
        } catch (e: any) {
            setError(e?.response?.data?.message ?? 'Failed to save position. Please try again.');
        }
    };

    if (!open) return null;

    const totalValue = selectedAsset && quantity
        ? parseFloat(quantity) * (selectedAsset.currentPrice ?? 0)
        : null;

    return (
        <>
            {/* Backdrop */}
            <div
                onClick={onClose}
                className={styles.backdrop}
            />

            {/* Drawer */}
            <div className={styles.drawer}>
                {/* Header */}
                <div className={styles.header}>
                    <h2>
                        {editingPositionId ? 'Edit Position' : 'Add Position'}
                    </h2>
                    <button
                        onClick={onClose}
                        className={styles.closeButton}
                        title="Close"
                    >✕</button>
                </div>

                {/* Body */}
                <div className={styles.body}>

                    {/* ── SEARCH (only when not editing and no asset selected) ── */}
                    {!editingPositionId && !selectedAsset && (
                        <>
                            <div>
                                <label style={labelStyle}>Search Asset</label>
                                <input
                                    type="text"
                                    placeholder="Search by name or ticker…"
                                    value={searchQuery}
                                    onChange={e => setSearchQuery(e.target.value)}
                                    style={inputStyle}
                                    onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = inputFocusStyle.borderColor!; }}
                                    onBlur={e  => { (e.currentTarget as HTMLElement).style.borderColor = inputBlurStyle.borderColor!; }}
                                    autoFocus
                                />
                            </div>

                            {/* Results list */}
                            {searchQuery.trim() && (
                                <div className={styles.searchResults}>
                                    {isSearching ? (
                                        <div className={styles.searchResultsLoading}>
                                            Searching…
                                        </div>
                                    ) : searchResults.length > 0 ? (
                                        searchResults.map(asset => (
                                            <button
                                                key={asset.ticker}
                                                onClick={() => { setSelectedAsset(asset); setSearchQuery(''); setSearchResults([]); }}
                                                className={styles.assetButton}
                                            >
                                                <div className={styles.assetButtonContent}>
                                                    <div className={styles.assetButtonName}>
                                                        {asset.name}
                                                    </div>
                                                    <div className={styles.assetButtonMeta}>
                                                        <span className={styles.assetButtonTicker}>{asset.ticker}</span>
                                                        <AssetTag type={asset.type as any} size="sm" />
                                                    </div>
                                                </div>
                                                {asset.currentPrice != null && (
                                                    <div className={styles.assetButtonPrice}>
                                                        ${asset.currentPrice.toFixed(2)}
                                                    </div>
                                                )}
                                            </button>
                                        ))
                                    ) : (
                                        <div className={styles.searchResultsNoResults}>
                                            No assets found for "{searchQuery}"
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Empty prompt */}
                            {!searchQuery.trim() && (
                                <div className={styles.searchResultsEmptyPrompt}>
                                    <div className={styles.searchResultsEmptyPromptIcon}>🔍</div>
                                    <p>Search for an asset to add to your portfolio</p>
                                </div>
                            )}
                        </>
                    )}

                    {/* ── SELECTED ASSET + QUANTITY ── */}
                    {selectedAsset && (
                        <>
                            {/* Selected asset card */}
                            <div className={styles.selectedAssetCard}>
                                <div className={styles.selectedAssetCardName}>
                                    {selectedAsset.name}
                                </div>
                                <div className={styles.selectedAssetCardMeta}>
                                    <div className={styles.selectedAssetCardMetaLeft}>
                                        <span className={styles.assetButtonTicker}>{selectedAsset.ticker}</span>
                                        <AssetTag type={selectedAsset.type as any} size="sm" />
                                    </div>
                                    {/* Only allow changing asset when adding, not editing */}
                                    {!editingPositionId && (
                                        <button
                                            onClick={() => { setSelectedAsset(null); setQuantity(''); }}
                                            className={styles.selectedAssetCardChangeButton}
                                            title="Change asset"
                                        >✕</button>
                                    )}
                                </div>
                            </div>

                            {/* Price + Type chips */}
                            <div className={styles.infoGrid}>
                                <div className={styles.infoCard}>
                                    <div className={styles.infoCardLabel}>Current Price</div>
                                    <div className={styles.infoCardValue}>
                                        {selectedAsset.currentPrice != null ? `$${selectedAsset.currentPrice.toFixed(2)}` : '—'}
                                    </div>
                                </div>
                                <div className={styles.infoCard}>
                                    <div className={styles.infoCardLabel}>Type</div>
                                    <AssetTag type={selectedAsset.type as any} size="md" />
                                </div>
                            </div>

                            {/* Quantity */}
                            <div>
                                <label style={labelStyle}>Quantity</label>
                                <input
                                    type="number"
                                    placeholder="Enter quantity…"
                                    value={quantity}
                                    onChange={e => { setQuantity(e.target.value); setError(null); }}
                                    step="0.01"
                                    min="0.01"
                                    style={{ ...inputStyle, fontFamily: 'var(--font-mono)' }}
                                    onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = inputFocusStyle.borderColor!; }}
                                    onBlur={e  => { (e.currentTarget as HTMLElement).style.borderColor = inputBlurStyle.borderColor!; }}
                                    autoFocus
                                />
                            </div>

                            {/* Summary */}
                            {quantity && !isNaN(parseFloat(quantity)) && parseFloat(quantity) > 0 && (
                                <div className={styles.summaryCard}>
                                    <div className={styles.summaryCardRow}>
                                        <span className={styles.summaryCardLabel}>Quantity</span>
                                        <span className={styles.summaryCardValue}>
                                            {parseFloat(quantity).toLocaleString()}
                                        </span>
                                    </div>
                                    {totalValue != null && selectedAsset.currentPrice != null && (
                                        <div className={styles.summaryCardRow}>
                                            <span className={styles.summaryCardLabel}>Est. Value</span>
                                            <span className={`${styles.summaryCardValue} ${styles.summaryCardValueHighlight}`}>
                                                ${totalValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                            </span>
                                        </div>
                                    )}
                                    {selectedAsset.currentPrice == null && (
                                        <div className={styles.summaryCardNote}>
                                            Price will be fetched at time of submission
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Error */}
                            {error && (
                                <div className={styles.errorCard}>
                                    {error}
                                </div>
                            )}
                        </>
                    )}
                </div>

                {/* Footer */}
                <div className={styles.footer}>
                    <ProbityButton variant="ghost" size="md" onClick={onClose} className={styles.footerButton}>
                        Cancel
                    </ProbityButton>
                    <ProbityButton
                        variant="primary"
                        size="md"
                        onClick={handleSubmit}
                        disabled={!selectedAsset || !quantity || parseFloat(quantity) <= 0}
                        loading={submitMutation.isPending}
                        className={styles.footerButton}
                    >
                        {editingPositionId ? 'Update' : 'Add to Portfolio'}
                    </ProbityButton>
                </div>
            </div>
        </>
    );
}