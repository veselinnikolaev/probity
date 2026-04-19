# Probity Design System: Implementation Quick Reference

**For detailed rules and reasoning, see [Global Behavior Rules](./GLOBAL_BEHAVIOR_RULES.md)**

---

## Checklist for New Features

Copy this checklist when starting a new feature:

- [ ] Define all 4 data states (loading, empty, error, partial)
- [ ] Use existing components (no custom rebuilds)
- [ ] Use `--pb-*` CSS variables (no hardcoded colors)
- [ ] Follow semantic HTML structure
- [ ] Format all numbers using `src/utils/formatters.ts`
- [ ] Handle edge cases (zero, null, large values, long text)
- [ ] Test keyboard navigation and screen readers
- [ ] Test on mobile (responsive, touch-friendly)

---

## Quick Code Patterns

### 1. Loading State

```tsx
import { SkeletonLoader } from '@/components/probity/SkeletonLoader';

export function Holdings() {
  const [holdings, setHoldings] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchHoldings()
      .then(setHoldings)
      .finally(() => setIsLoading(false));
  }, []);

  if (isLoading) {
    return <SkeletonLoader type="table" rows={8} />;
  }

  return <HoldingsTable data={holdings} />;
}
```

**Always:**
- Show skeleton immediately (reserved space)
- Match skeleton layout to final content
- Animate shimmer left-to-right
- Keep sidebar/navigation interactive

---

### 2. Empty State

```tsx
import { EmptyState } from '@/components/probity/EmptyState';
import { BriefcaseIcon } from '@/components/icons/BriefcaseIcon';

export function PortfolioList({ portfolios }) {
  if (portfolios.length === 0) {
    return (
      <EmptyState
        icon={<BriefcaseIcon />}
        title="No portfolios yet"
        description="Create your first portfolio to get started analyzing your assets."
        actionLabel="Create Portfolio"
        onAction={() => openCreateModal()}
      />
    );
  }

  return <PortfolioGrid portfolios={portfolios} />;
}
```

**Always:**
- Use `EmptyState` component (reuse, don't rebuild)
- Include: icon, title, description, primary CTA
- Title pattern: "[Entity] not found" or "No [entities]"
- Description: What to do next

---

### 3. Error State

```tsx
export function PortfolioView({ portfolioId }) {
  const [portfolio, setPortfolio] = useState(null);
  const [error, setError] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchPortfolio(portfolioId)
      .then(setPortfolio)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [portfolioId]);

  if (error) {
    return (
      <div style={{
        border: '1px solid var(--pb-danger)',
        backgroundColor: 'rgba(239,68,68,0.08)',
        borderRadius: '8px',
        padding: '16px',
        display: 'flex',
        gap: '12px',
      }}>
        <AlertTriangleIcon color="var(--pb-danger)" />
        <div>
          <h3 style={{ color: 'var(--pb-text-primary)', margin: '0 0 4px 0' }}>
            Failed to load portfolio
          </h3>
          <p style={{ color: 'var(--pb-text-secondary)', margin: '0 0 12px 0', fontSize: '14px' }}>
            {error || 'Check your connection and try again.'}
          </p>
          <button onClick={() => window.location.reload()}>
            Try Again
          </button>
        </div>
      </div>
    );
  }

  // ... rest of component
}
```

**Always:**
- Human-readable error messages (no system errors)
- Pair with icon and text (not color alone)
- Include retry action when possible
- Use danger color: `var(--pb-danger)` (#EF4444)

---

### 4. Partial Data (Some sections fail)

```tsx
export function Dashboard() {
  const [metrics, setMetrics] = useState(null);
  const [holdings, setHoldings] = useState(null);
  const [metricsError, setMetricsError] = useState(null);

  useEffect(() => {
    fetchMetrics().catch(err => setMetricsError(err.message));
    fetchHoldings().then(setHoldings);
  }, []);

  return (
    <>
      {metricsError && (
        <div style={{
          border: '1px solid var(--pb-warning)',
          backgroundColor: 'rgba(245,158,11,0.08)',
          padding: '12px',
          borderRadius: '8px',
          marginBottom: '16px',
          color: 'var(--pb-text-secondary)',
        }}>
          <AlertCircleIcon /> Metric data unavailable. Showing holdings instead.
        </div>
      )}
      
      {metrics ? <MetricCards data={metrics} /> : null}
      {holdings ? <HoldingsTable data={holdings} /> : null}
    </>
  );
}
```

**Always:**
- Render available data normally
- Show warning for missing sections
- Never block entire UI for partial failures

---

### 5. Data Formatting

```tsx
import {
  formatCurrency,
  formatPercentage,
  formatDate,
  formatGainLoss,
  formatMissing,
  truncateText,
} from '@/utils/formatters';

export function HoldingsRow({ holding }) {
  const { truncated: assetName, full: fullAssetName } = truncateText(
    holding.assetName,
    30
  );

  return (
    <tr>
      <td>
        <Tooltip content={fullAssetName}>
          <span>{assetName}</span>
        </Tooltip>
      </td>
      <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--pb-text-primary)' }}>
        {formatCurrency(holding.quantity * holding.price)}
      </td>
      <td style={{ fontFamily: 'var(--font-mono)', color: getSemanticColor(holding.gainLoss).color }}>
        {formatGainLoss(holding.gainLoss, 'currency')}
      </td>
      <td style={{ fontFamily: 'var(--font-mono)', color: getSemanticColor(holding.returnPercent).color }}>
        {formatGainLoss(holding.returnPercent, 'percentage')}
      </td>
      <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--pb-text-secondary)' }}>
        {formatDate(holding.purchaseDate)}
      </td>
      <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--pb-text-muted)' }}>
        {formatMissing(holding.notes)}
      </td>
    </tr>
  );
}
```

**Always:**
- Use formatters for ALL numeric data
- Use `var(--font-mono)` for numbers/currency/percentages
- Use `formatGainLoss` for positive/negative values
- Use `formatMissing` for null/undefined values (shows "—")
- Use `truncateText` for long strings + tooltips

---

### 6. Buttons with Loading States

```tsx
import { ProbityButton } from '@/components/probity/ProbityButton';

export function SavePortfolioButton({ portfolioId, data }) {
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await updatePortfolio(portfolioId, data);
      showToast({
        type: 'success',
        message: 'Portfolio saved',
      });
    } catch (err) {
      showToast({
        type: 'error',
        message: 'Failed to save portfolio. Try again.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <ProbityButton
      onClick={handleSave}
      disabled={isSaving}
      isLoading={isSaving}
    >
      {isSaving ? 'Saving…' : 'Save'}
    </ProbityButton>
  );
}
```

**Always:**
- Disable button while processing
- Show loading state inside button
- Provide user feedback via toast

---

### 7. Semantic HTML Structure

```tsx
export function PortfolioDetail() {
  return (
    <main>
      <h1>Acme Corp Retirement Fund</h1>
      
      <section>
        <h2>Asset Allocation</h2>
        <AllocationChart />
      </section>

      <section>
        <h2>Holdings</h2>
        <table>
          <thead>
            <tr>
              <th scope="col">Asset</th>
              <th scope="col">Value</th>
              <th scope="col">Return</th>
            </tr>
          </thead>
          <tbody>
            {holdings.map(h => (
              <tr key={h.id}>
                <td>{h.name}</td>
                <td>{formatCurrency(h.value)}</td>
                <td>{formatPercentage(h.return)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </main>
  );
}
```

**Always:**
- One `<h1>` per page (page title)
- Use `<h2>` for major sections
- Use `<h3>` for subsections
- Use `<thead>`, `<tbody>`, `<th scope="col">` for tables
- Never skip heading levels

---

### 8. Form Inputs

```tsx
import { InputField } from '@/components/probity/InputField';

export function CreatePortfolioForm() {
  const [name, setName] = useState('');

  return (
    <form onSubmit={handleSubmit}>
      <InputField
        id="portfolio-name"
        label="Portfolio Name"
        placeholder="e.g., Retirement Fund"
        value={name}
        onChange={e => setName(e.target.value)}
        required
      />
      
      <button type="submit">Create</button>
    </form>
  );
}
```

**Always:**
- Use `<label>` with `htmlFor` attribute
- Never rely on placeholder alone as label
- Use `InputField` component or proper HTML label structure

---

### 9. Tooltips

```tsx
import { Tooltip } from '@/components/probity/Tooltip';

export function RiskMetric({ value, description }) {
  return (
    <Tooltip content={description}>
      <button type="button" aria-describedby="risk-info">
        <InfoIcon /> {formatPercentage(value)}
      </button>
    </Tooltip>
  );
}
```

**Always:**
- Make trigger element focusable (button, link, tabindex="0")
- Use `aria-describedby` for accessibility
- Delay appearance ~150ms to avoid flashing

---

### 10. Color + Icon Pairing

```tsx
// Bad: Color alone
<span style={{ color: '#EF4444' }}>●</span>

// Good: Color + Icon + Text
<div style={{ display: 'flex', gap: '8px', color: 'var(--pb-success)' }}>
  <CheckmarkIcon />
  <span>Position added</span>
</div>

// Good: With semantic helper
export function StatusBadge({ status }) {
  const semanticColor = getSemanticColor(status.value);
  
  const iconMap = {
    up: <TrendingUpIcon />,
    down: <TrendingDownIcon />,
    dash: <DashIcon />,
  };

  return (
    <div style={{ 
      display: 'flex', 
      gap: '8px',
      color: semanticColor.color 
    }}>
      {iconMap[semanticColor.icon]}
      <span>{status.label}</span>
    </div>
  );
}
```

**Always:**
- Pair colors with icons, labels, or text
- Use `getSemanticColor()` helper from formatters
- Never rely on color alone for meaning

---

### 11. Toast Notifications

```tsx
// Success
showToast({
  type: 'success',
  message: 'Portfolio created successfully',
  duration: 4000, // auto-dismiss
});

// Error (stays until user closes)
showToast({
  type: 'error',
  message: 'Failed to load portfolio data. Try again.',
  dismissible: true,
});

// Info
showToast({
  type: 'info',
  message: '3 assets added to portfolio',
  duration: 4000,
});

// Warning
showToast({
  type: 'warning',
  message: 'Portfolio data is refreshing',
  duration: 5000,
});
```

**Always:**
- Use correct type (success, error, info, warning)
- Message pattern: "[Action] [result]" or "[Entity] [verb]ed"
- Error messages are actionable and human-readable
- Position: top-right

---

### 12. Edge Cases

```tsx
import { formatCurrency, formatMissing, formatZero } from '@/utils/formatters';

export function MetricCard({ value, label }) {
  return (
    <div>
      <h3>{label}</h3>
      
      {/* Zero value: display "0.00", not blank */}
      <p style={{ fontFamily: 'var(--font-mono)' }}>
        {formatZero(value, 'currency')}
      </p>
      
      {/* Missing data: use em-dash */}
      <p style={{ color: 'var(--pb-text-muted)' }}>
        {formatMissing(value)}
      </p>
      
      {/* Large values: abbreviate */}
      <p style={{ fontFamily: 'var(--font-mono)' }}>
        {formatCurrency(value, true)} {/* Shows $1.2M */}
      </p>
    </div>
  );
}
```

**Always:**
- Display zero values as "0.00" not blank
- Use "—" for missing/null data
- Abbreviate large numbers ($1.2M)
- Truncate long text with ellipsis + tooltip

---

### 13. Progressive Loading

```tsx
export function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState(null);
  const [chart, setChart] = useState(null);

  useEffect(() => {
    // Load critical path first (metrics)
    fetchSummary().then(setSummary);
    
    // Then secondary path (holdings)
    fetchHoldings().then(setHoldings);
    
    // Finally tertiary path (chart)
    fetchChart().then(setChart);
  }, []);

  return (
    <>
      {/* Always visible */}
      <Navigation />
      
      {/* Load in priority order with skeletons */}
      <section>
        {summary ? <SummaryCards /> : <SkeletonLoader type="card" count={3} />}
      </section>
      
      <section>
        {holdings ? <HoldingsTable /> : <SkeletonLoader type="table" />}
      </section>
      
      <section>
        {chart ? <Chart /> : <SkeletonLoader type="chart" />}
      </section>
    </>
  );
}
```

**Always:**
- Load above-the-fold content first (metrics)
- Show layout immediately with skeletons
- Replace sections as data arrives
- Keep navigation interactive during loading

---

### 14. Hover & Interaction States

```tsx
// Table row hover
<tr style={{
  '&:hover': {
    backgroundColor: 'var(--pb-surface-elevated)',
    transition: '100ms ease-in-out',
    cursor: 'pointer',
  }
}}>
  {/* Content */}
</tr>

// Button hover (use ProbityButton component)
<ProbityButton variant="primary">
  {/* Hover state built in */}
</ProbityButton>

// Link hover
<a style={{
  color: 'var(--pb-accent)',
  textDecoration: 'none',
  '&:hover': {
    textDecoration: 'underline',
  }
}}>
  View Details
</a>
```

**Always:**
- Use `var(--pb-surface-elevated)` for hover background
- Transition: 100–150ms ease-in-out
- Reuse component hover states (don't rebuild)

---

## Token Reference

```css
/* Colors */
--pb-bg:               #0A0D12  /* Page background */
--pb-surface:          #0E1420  /* Card/surface */
--pb-surface-elevated: #131B2E  /* Hover, emphasis */
--pb-border:           #1E2D45  /* Dividers */
--pb-accent:           #3B82F6  /* Primary, info */
--pb-accent-hover:     #60A5FA  /* Primary hover */
--pb-success:          #10B981  /* Positive */
--pb-warning:          #F59E0B  /* Caution */
--pb-danger:           #EF4444  /* Error, danger */
--pb-text-primary:     #F1F5F9  /* Primary text */
--pb-text-secondary:   #64748B  /* Secondary text */
--pb-text-muted:       #334155  /* Disabled text */

/* Typography */
--font-display:  'Syne', sans-serif           /* Headings */
--font-body:     'DM Sans', sans-serif        /* Body */
--font-mono:     'IBM Plex Mono', monospace   /* Numbers */
```

---

## Component Reference

| Component | Path | Use Case |
|-----------|------|----------|
| `SkeletonLoader` | `probity/SkeletonLoader.tsx` | Loading state |
| `EmptyState` | `probity/EmptyState.tsx` | Empty data |
| `ProbityButton` | `probity/ProbityButton.tsx` | Actions |
| `InputField` | `probity/InputField.tsx` | Form inputs |
| `DataTable` | `probity/DataTable.tsx` | Data tables |
| `StatCard` | `probity/StatCard.tsx` | Metric cards |
| `Modal` | `probity/Modal.tsx` | Dialogs |
| `Tooltip` | `probity/Tooltip.tsx` | Help text |
| `DonutChart` | `probity/DonutChart.tsx` | Allocation charts |
| `LineChart` | `probity/LineChart.tsx` | Trend charts |

---

## Utilities Reference

All from `src/utils/formatters.ts`:

| Function | Purpose | Example |
|----------|---------|---------|
| `formatCurrency()` | Format as currency | `$1,234.50` or `$1.2M` |
| `formatPercentage()` | Format as percentage | `12.45%` |
| `formatDate()` | Format as ISO date | `2026-01-15` |
| `formatNumber()` | Format with separators | `1,234,567.00` |
| `formatGainLoss()` | Format with +/– sign | `+$5,000.00` or `–$2,450.32` |
| `formatMissing()` | Handle null/undefined | `—` |
| `truncateText()` | Truncate + ellipsis | `"Long Text…"` with tooltip |
| `formatZero()` | Format zero values | `$0.00` |
| `getSemanticColor()` | Color for value | green, red, or neutral |
| `shouldUseMonospace()` | Check if needs monospace | true/false |

---

## Running Tests

Before shipping:

```bash
# Type check
npm run type-check

# Lint
npm run lint

# Test accessibility (manual)
- Tab through all interactive elements
- Check focus states visible
- Test with screen reader
- Check color contrast

# Test responsive (manual)
- Desktop (1920px)
- Tablet (768px)
- Mobile (375px)

# Test data states (manual)
- ✓ Loading skeleton appears
- ✓ Empty state shows when no data
- ✓ Error state shows on failure
- ✓ Data loads without layout shift
```

---

**For detailed explanations and rationale, see [Global Behavior Rules](./GLOBAL_BEHAVIOR_RULES.md)**
