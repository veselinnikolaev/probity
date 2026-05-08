# Quick Reference Card

## File Locations

| File | Purpose | Lines |
|------|---------|-------|
| `src/types/simulation.ts` | Type definitions | 86 |
| `src/api/simulationService.ts` | API service + mock | 145 |
| `src/components/probity/FanChart.tsx` | Path visualization | 274 |
| `src/components/probity/DistributionHistogram.tsx` | Distribution chart | 242 |
| `src/components/probity/PercentileTable.tsx` | Outcomes table | 152 |
| `src/pages/monte-carlo/MonteCarlo.tsx` | Main page | 396 |
| `src/pages/monte-carlo/monte-carlo.module.css` | Styles | 475 |

---

## Import Guide

```typescript
// Types
import { SimulationRequest, SimulationResult } from '@/types/simulation';

// Services
import { runSimulation } from '@/api/simulationService';

// Components
import { FanChart } from '@/components/probity/FanChart';
import { DistributionHistogram } from '@/components/probity/DistributionHistogram';
import { PercentileTable } from '@/components/probity/PercentileTable';
import { StatCard, StatCardSkeleton } from '@/components/probity/StatCard';
import { DataBoundary } from '@/components/core/DataBoundary';
import { EmptyState, ErrorState } from '@/components/probity/EmptyState';

// Styles
import styles from '@/pages/monte-carlo/monte-carlo.module.css';
```

---

## Route Navigation

| Route | Component | Purpose |
|-------|-----------|---------|
| `/dashboard` | DashboardPage | Main overview |
| `/portfolios` | PortfoliosPage | Portfolio list |
| `/portfolio-detail` | PortfolioDetailPage | Portfolio editor |
| `/risk` | RiskAnalysisPage | Risk metrics |
| `/var` | VaRReportPage | Value at Risk |
| `/monte` | MonteCarloPage | **Monte Carlo (NEW)** |

---

## Color Tokens

```css
/* Use these, not hardcoded colors */
--pb-bg: #0A0D12
--pb-surface: #0E1420
--pb-surface-elevated: #131B2E
--pb-border: #1E2D45
--pb-accent: #3B82F6 (blue)
--pb-success: #10B981 (green)
--pb-warning: #F59E0B (amber)
--pb-danger: #EF4444 (red)
--pb-text-primary: #F1F5F9
--pb-text-secondary: #64748B
--pb-text-muted: #334155
```

---

## Spacing Scale

```
4px, 6px, 8px, 10px, 12px, 16px, 20px, 24px, 32px, 40px, 48px, 60px
```

Common usage:
```css
padding: 32px /* pages */
padding: 20px /* cards */
gap: 32px /* sections */
gap: 16px /* items */
border-radius: 6px /* controls */
border-radius: 8px /* cards */
```

---

## Typography Scale

```
H1: 32px (2rem)    - Page title
H2: 24px (1.5rem)  - Section title
H3: 20px (1.25rem) - Card title
Body: 13px         - Standard text
Small: 11px        - Labels, secondary
```

Fonts:
```
Display: var(--font-display)  /* Syne */
Body: var(--font-body)        /* DM Sans */
Mono: var(--font-mono)        /* IBM Plex Mono */
```

---

## Responsive Breakpoints

```
Desktop: ≥1024px
  - 240px sidebar
  - 32px padding
  - Multi-column grids
  - Full controls visible

Tablet: 768–1023px
  - 200px sidebar
  - 24px padding
  - 2-column grids
  - Wrapped controls

Mobile: <768px
  - Hidden sidebar (drawer)
  - 16px padding
  - 1-column stacked
  - Touch-friendly (44px+)
```

---

## Common Classes

```css
.layout              /* Main container */
.sidebar             /* Navigation sidebar */
.topbar              /* Header bar */
.content-area        /* Scrollable content */
.content-container   /* Max-width wrapper */
.section-title       /* H2 style */
.controls-bar        /* Control section */
.chart-container     /* Card for charts */
.outcomes-grid       /* Card grid */
.loading-state       /* Loading message */
.empty-state-container /* Empty message */
```

---

## Common Components

```jsx
// Loading
<Skeleton width="100%" height="40px" />
<StatCardSkeleton />
<TableSkeleton rows={5} />

// States
<DataBoundary state={state} isEmpty={...} loading={...} empty={...} error={...}>
  {(data) => <Content data={data} />}
</DataBoundary>

// Cards
<StatCard label="Metric" value="$100k" delta="+5%" deltaType="up" />

// Messages
<EmptyState title="No Data" description="..." />
<ErrorState message="Failed to load" onRetry={refetch} />
```

---

## Async State Pattern

```typescript
const [state, setState] = useState<AsyncState<T>>({ status: 'idle', data: null });

// In handler
try {
  setState({ status: 'loading', data: null });
  const result = await fetchData();
  setState({ status: 'success', data: result });
} catch (error) {
  setState({ status: 'error', data: null, error: error.message });
}

// In render
if (state.status === 'loading') return <Skeleton />;
if (state.status === 'error') return <ErrorState message={state.error} />;
if (!state.data) return <EmptyState />;
return <Content data={state.data} />;
```

---

## CSS Module Best Practices

```typescript
// Import
import styles from './page.module.css';

// Use
<div className={styles['section-title']}>…</div>
<button className={`${styles['button']} ${active ? styles['active'] : ''}`}>…</button>

// Not: className="section-title" or style={{ fontSize: '20px' }}
```

---

## TypeScript Best Practices

```typescript
// Use interfaces for contracts
interface MyProps {
  data: SimulationResult;
  onRun: (request: SimulationRequest) => Promise<void>;
}

// Export types at module level
export type { MyProps };
export interface MyData { ... }

// Use optional chaining and nullish coalescing
const value = data?.result ?? defaultValue;

// Avoid inline types, use const assertions
const OPTIONS = [
  { label: 'Option 1', value: '1' },
  { label: 'Option 2', value: '2' },
] as const;
```

---

## Debugging Tips

1. **Check build**: `npm run build`
2. **Type errors**: Look at console during dev server
3. **Layout issues**: Browser DevTools → Elements → Computed styles
4. **Async issues**: React DevTools → Profiler → check render times
5. **API issues**: Network tab in DevTools → check request/response

---

## Common Gotchas

```typescript
// ❌ Don't: Hardcoded colors
style={{ color: '#3B82F6' }}

// ✅ Do: Use CSS variables
style={{ color: 'var(--pb-accent)' }}

// ❌ Don't: Inline all styles
<div style={{ padding: '20px', fontSize: '16px', ... }}>

// ✅ Do: Use CSS modules
<div className={styles['card']}>

// ❌ Don't: Forget error states
<DataBoundary state={state} isEmpty={...} loading={...}>
  {(data) => <Content data={data} />}
</DataBoundary>

// ✅ Do: Include all states
<DataBoundary state={state} isEmpty={...} loading={...} empty={...} error={...}>
  {(data) => <Content data={data} />}
</DataBoundary>
```

---

## API Integration

### Current (Mock)
```typescript
// src/api/simulationService.ts
export async function runSimulation(...) {
  return generateMockSimulation(request, currentValue);
}
```

### To Backend (TODO)
```typescript
const response = await api.post<SimulationResponse>('/simulations/run', request);
return response.data.data;
```

---

## Environment Variables

```env
REACT_APP_API_URL=http://localhost:3000/api
REACT_APP_ENV=development
```

---

## Performance Tips

1. **Canvas charts**: Use for 1000+ items
2. **Memoization**: Wrap expensive computes in `useMemo`
3. **Debouncing**: Debounce resize/search handlers
4. **Lazy loading**: Code-split pages if bundle > 1MB
5. **Caching**: Cache API results with TTL

---

## Accessibility Checklist

- [ ] Semantic HTML (`<button>`, `<table>`, `<label>`)
- [ ] Focus indicators (outline ring visible)
- [ ] ARIA labels for icons (`aria-label`)
- [ ] Color contrast ≥ 4.5:1
- [ ] Keyboard navigation (Tab, Enter, Arrow keys)
- [ ] Alt text for images
- [ ] Screen reader friendly (test with NVDA)
- [ ] Reduced motion support (`@media (prefers-reduced-motion)`)

---

## Testing Checklist

- [ ] Page loads without errors
- [ ] All controls work
- [ ] Charts render correctly
- [ ] Loading states appear
- [ ] Error states display with retry
- [ ] Empty states show when no data
- [ ] Responsive on all breakpoints
- [ ] Keyboard navigation works
- [ ] Focus states visible
- [ ] Color contrast sufficient
- [ ] No console errors/warnings

---

## Useful Commands

```bash
# Development
npm run dev           # Start dev server
npm run build         # Build for production
npm run build 2>&1 | grep error  # Check for errors

# Linting
npx eslint src --ext .ts,.tsx
npx tsc --noEmit      # Type check

# Testing
npm test              # Run tests (if configured)
```

---

## Resources

- [Design System Guide](./DESIGN_SYSTEM.md)
- [Component Examples](./COMPONENT_EXAMPLES.md)
- [Backend Integration](./BACKEND_INTEGRATION.md)
- [Implementation Summary](./IMPLEMENTATION_SUMMARY.md)
- [Changes Manifest](./CHANGES.md)

---

**Last Updated**: May 8, 2026
