import React, { useState, useEffect, useRef } from 'react';
import { AssetTag } from './AssetTag';
import { ProbityButton } from './ProbityButton';
import { portfolioService } from '../../api/portfolioService';
import type { AssetSearchResult } from '../../types/portfolio';

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
    const [isLoading, setIsLoading]             = useState(false);
    const [error, setError]                     = useState<string | null>(null);
    const debounceRef                           = useRef<ReturnType<typeof setTimeout> | null>(null);

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

        setIsLoading(true);
        setError(null);
        try {
            if (editingPositionId) {
                await portfolioService.updatePosition(portfolioId, editingPositionId, { quantity: qty });
            } else {
                await portfolioService.addPosition(portfolioId, {
                    ticker: selectedAsset.ticker,
                    quantity: qty,
                });
            }
            setSelectedAsset(null);
            setQuantity('');
            setSearchQuery('');
            setSearchResults([]);
            onSuccess?.();
            onClose();
        } catch (e: any) {
            setError(e?.response?.data?.message ?? 'Failed to save position. Please try again.');
        } finally {
            setIsLoading(false);
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
                style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 999, animation: 'fade-in 0.2s ease-out' }}
            />

            {/* Drawer */}
            <div style={{
                position: 'fixed', right: 0, top: 0, bottom: 0, width: 420,
                backgroundColor: 'var(--pb-surface)',
                borderLeft: '1px solid var(--pb-border)',
                display: 'flex', flexDirection: 'column',
                zIndex: 1000,
                animation: 'slide-in 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
                boxShadow: '-4px 0 16px rgba(0,0,0,0.3)',
            }}>
                {/* Header */}
                <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--pb-border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
                    <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 700, color: 'var(--pb-text-primary)', margin: 0 }}>
                        {editingPositionId ? 'Edit Position' : 'Add Position'}
                    </h2>
                    <button
                        onClick={onClose}
                        style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 32, height: 32, background: 'transparent', border: 'none', color: 'var(--pb-text-secondary)', fontSize: 20, cursor: 'pointer' }}
                        title="Close"
                    >✕</button>
                </div>

                {/* Body */}
                <div style={{ flex: 1, overflowY: 'auto', padding: 24, display: 'flex', flexDirection: 'column', gap: 20 }}>

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
                                    onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.4)'; }}
                                    onBlur={e  => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)'; }}
                                    autoFocus
                                />
                            </div>

                            {/* Results list */}
                            {searchQuery.trim() && (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 360, overflowY: 'auto' }}>
                                    {isSearching ? (
                                        <div style={{ textAlign: 'center', padding: '32px 16px', color: 'var(--pb-text-secondary)', fontSize: 14 }}>
                                            Searching…
                                        </div>
                                    ) : searchResults.length > 0 ? (
                                        searchResults.map(asset => (
                                            <button
                                                key={asset.ticker}
                                                onClick={() => { setSelectedAsset(asset); setSearchQuery(''); setSearchResults([]); }}
                                                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, backgroundColor: 'var(--pb-surface-elevated)', border: '1px solid var(--pb-border)', borderRadius: 6, cursor: 'pointer', textAlign: 'left', transition: 'all 0.12s', width: '100%' }}
                                                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(59,130,246,0.1)'; (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.3)'; }}
                                                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.backgroundColor = 'var(--pb-surface-elevated)'; (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)'; }}
                                            >
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 500, color: 'var(--pb-text-primary)', marginBottom: 4 }}>
                                                        {asset.name}
                                                    </div>
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 500, color: 'var(--pb-text-secondary)' }}>{asset.ticker}</span>
                                                        <AssetTag type={asset.type as any} size="sm" />
                                                    </div>
                                                </div>
                                                {asset.currentPrice != null && (
                                                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 500, color: 'var(--pb-accent)', flexShrink: 0 }}>
                                                        ${asset.currentPrice.toFixed(2)}
                                                    </div>
                                                )}
                                            </button>
                                        ))
                                    ) : (
                                        <div style={{ textAlign: 'center', padding: '32px 16px', color: 'var(--pb-text-secondary)', fontSize: 14 }}>
                                            No assets found for "{searchQuery}"
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Empty prompt */}
                            {!searchQuery.trim() && (
                                <div style={{ textAlign: 'center', padding: '48px 16px', color: 'var(--pb-text-secondary)' }}>
                                    <div style={{ fontSize: 32, marginBottom: 12, opacity: 0.6 }}>🔍</div>
                                    <p style={{ margin: 0, fontSize: 14 }}>Search for an asset to add to your portfolio</p>
                                </div>
                            )}
                        </>
                    )}

                    {/* ── SELECTED ASSET + QUANTITY ── */}
                    {selectedAsset && (
                        <>
                            {/* Selected asset card */}
                            <div style={{ padding: 12, backgroundColor: 'rgba(59,130,246,0.05)', border: '1px solid rgba(59,130,246,0.2)', borderRadius: 6 }}>
                                <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 500, color: 'var(--pb-text-primary)', marginBottom: 4 }}>
                                    {selectedAsset.name}
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 500, color: 'var(--pb-text-secondary)' }}>{selectedAsset.ticker}</span>
                                        <AssetTag type={selectedAsset.type as any} size="sm" />
                                    </div>
                                    {/* Only allow changing asset when adding, not editing */}
                                    {!editingPositionId && (
                                        <button
                                            onClick={() => { setSelectedAsset(null); setQuantity(''); }}
                                            style={{ background: 'transparent', border: 'none', color: 'var(--pb-text-secondary)', cursor: 'pointer', fontSize: 14 }}
                                            title="Change asset"
                                        >✕</button>
                                    )}
                                </div>
                            </div>

                            {/* Price + Type chips */}
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                <div style={{ padding: 12, backgroundColor: 'var(--pb-surface-elevated)', borderRadius: 6 }}>
                                    <div style={{ fontSize: 11, color: 'var(--pb-text-secondary)', marginBottom: 4, textTransform: 'uppercase', fontWeight: 500, letterSpacing: '0.06em' }}>Current Price</div>
                                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: 16, fontWeight: 600, color: 'var(--pb-accent)' }}>
                                        {selectedAsset.currentPrice != null ? `$${selectedAsset.currentPrice.toFixed(2)}` : '—'}
                                    </div>
                                </div>
                                <div style={{ padding: 12, backgroundColor: 'var(--pb-surface-elevated)', borderRadius: 6 }}>
                                    <div style={{ fontSize: 11, color: 'var(--pb-text-secondary)', marginBottom: 4, textTransform: 'uppercase', fontWeight: 500, letterSpacing: '0.06em' }}>Type</div>
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
                                    onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.4)'; }}
                                    onBlur={e  => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--pb-border)'; }}
                                    autoFocus
                                />
                            </div>

                            {/* Summary */}
                            {quantity && !isNaN(parseFloat(quantity)) && parseFloat(quantity) > 0 && (
                                <div style={{ padding: 12, backgroundColor: 'rgba(16,185,129,0.05)', border: '1px solid rgba(16,185,129,0.2)', borderRadius: 6 }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                        <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--pb-text-secondary)' }}>Quantity</span>
                                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 500, color: 'var(--pb-text-primary)' }}>
                                            {parseFloat(quantity).toLocaleString()}
                                        </span>
                                    </div>
                                    {totalValue != null && selectedAsset.currentPrice != null && (
                                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                            <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--pb-text-secondary)' }}>Est. Value</span>
                                            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 600, color: '#10B981' }}>
                                                ${totalValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                            </span>
                                        </div>
                                    )}
                                    {selectedAsset.currentPrice == null && (
                                        <div style={{ fontSize: 12, color: 'var(--pb-text-secondary)', marginTop: 4 }}>
                                            Price will be fetched at time of submission
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Error */}
                            {error && (
                                <div style={{ padding: '10px 12px', backgroundColor: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.25)', borderRadius: 6, fontSize: 13, color: '#EF4444', fontFamily: 'var(--font-body)' }}>
                                    {error}
                                </div>
                            )}
                        </>
                    )}
                </div>

                {/* Footer */}
                <div style={{ padding: '16px 24px', borderTop: '1px solid var(--pb-border)', display: 'flex', gap: 12, flexShrink: 0 }}>
                    <ProbityButton variant="ghost" size="md" onClick={onClose} style={{ flex: 1 }}>
                        Cancel
                    </ProbityButton>
                    <ProbityButton
                        variant="primary"
                        size="md"
                        onClick={handleSubmit}
                        disabled={!selectedAsset || !quantity || parseFloat(quantity) <= 0}
                        loading={isLoading}
                        style={{ flex: 1 }}
                    >
                        {editingPositionId ? 'Update' : 'Add to Portfolio'}
                    </ProbityButton>
                </div>

                <style>{`
                    @keyframes slide-in {
                        from { transform: translateX(100%); opacity: 0; }
                        to   { transform: translateX(0);    opacity: 1; }
                    }
                    @keyframes fade-in {
                        from { opacity: 0; }
                        to   { opacity: 1; }
                    }
                `}</style>
            </div>
        </>
    );
}