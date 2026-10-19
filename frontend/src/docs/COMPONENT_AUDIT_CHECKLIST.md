# Probity Design System: Component Audit Checklist

Use this checklist to audit existing and new components for compliance with the Global Behavior Rules.

---

## Quick Audit Checklist (5 min per component)

For each component, verify:

- [ ] All 4 data states are handled (loading, empty, error, partial)
- [ ] Component uses design tokens (`--pb-*`), not hardcoded colors
- [ ] Numbers formatted with `formatters.ts` utilities
- [ ] Semantic HTML is correct (headings, tables, labels, buttons vs links)
- [ ] Keyboard navigation works (Tab, Shift+Tab, Escape, Enter)
- [ ] Color signals paired with icons/text (not color alone)
- [ ] No custom variants rebuilt (reuses existing components)
- [ ] Micro-interactions are <200ms
- [ ] Mobile responsive tested

---

## Data States Audit

### ✅ Component supports loading state

**Checklist:**
- [ ] Uses `SkeletonLoader` component (not spinner)
- [ ] Skeleton matches exact layout/shape of final component
- [ ] Skeleton has shimmer animation (left-to-right)
- [ ] No layout shift when content loads
- [ ] Tables: 5–8 skeleton rows
- [ ] Charts: placeholder grid + faded line structure

**Bad:**
```tsx
{isLoading ? <Spinner /> : <DataTable />}
```

**Good:**
```tsx
{isLoading ? <SkeletonLoader type="table" rows={8} /> : <DataTable />}
```

---

### ✅ Component supports empty state

**Checklist:**
- [ ] Uses `EmptyState` component (reused, not custom)
- [ ] Has dashed border (`1px dashed var(--pb-border)`)
- [ ] Has meaningful icon
- [ ] Has clear heading (h3, Syne font)
- [ ] Has instructional description text
- [ ] Has primary CTA button (when applicable)
- [ ] Message pattern: "[Entity] not found" + "[Action needed]"

**Bad:**
```tsx
{data.length === 0 ? <p>No data</p> : <Table data={data} />}
```

**Good:**
```tsx
{data.length === 0 ? (
  <EmptyState
    icon={<BriefcaseIcon />}
    title="No portfolios yet"
    description="Create your first portfolio to get started."
    actionLabel="Create Portfolio"
    onAction={openCreateModal}
  />
) : (
  <Table data={data} />
)}
```

---

### ✅ Component supports error state

**Checklist:**
- [ ] Error appears inline (not full-page blocking)
- [ ] Uses danger color: `var(--pb-danger)` (#EF4444)
- [ ] Has danger icon (alert, warning triangle, etc.)
- [ ] Message is human-readable (no system errors)
- [ ] Message pattern: "[Action] failed. [Suggestion]"
- [ ] Includes retry action (button or link)
- [ ] Error container: border + background (not just text)

**Bad:**
```tsx
{error && <p style={{ color: 'red' }}>{error.code}</p>}
```

**Good:**
```tsx
{error && (
  <div style={{
    border: '1px solid var(--pb-danger)',
    backgroundColor: 'rgba(239,68,68,0.08)',
    padding: '16px',
    borderRadius: '8px',
  }}>
    <AlertIcon color="var(--pb-danger)" />
    <h3>Failed to load data</h3>
    <p>{error.userMessage}</p>
    <button onClick={retry}>Try Again</button>
  </div>
)}
```

---

### ✅ Component supports partial data

**Checklist:**
- [ ] Renders available data normally
- [ ] Shows inline warning for missing sections
- [ ] Does not block entire UI for partial failures
- [ ] Warning uses `var(--pb-warning)` (#F59E0B)
- [ ] Message explains what's missing and what's visible

**Bad:**
```tsx
if (!metrics || !holdings) {
  return <ErrorMessage />;
}
return <Dashboard metrics={metrics} holdings={holdings} />;
```

**Good:**
```tsx
return (
  <>
    {metricsError && <WarningBanner message="Metrics unavailable. Showing holdings." />}
    {metrics && <MetricsSection />}
    {holdings && <HoldingsSection />}
  </>
);
```

---

## Semantic HTML Audit

### ✅ Heading structure is correct

**Checklist:**
- [ ] Page has exactly one `<h1>` (page title)
- [ ] Sections use `<h2>` or `<h3>`
- [ ] No heading levels skipped (e.g., `<h1>` → `<h3>`)
- [ ] All headings use Syne font (`--font-display`)
- [ ] Headings are in logical order

**Bad:**
```tsx
<div>
  <h1>Dashboard</h1>
  <h3>Asset Allocation</h3>  {/* Skipped h2! */}
  <h1>Holdings</h1>  {/* Multiple h1s! */}
</div>
```

**Good:**
```tsx
<main>
  <h1>Dashboard</h1>
  <section>
    <h2>Asset Allocation</h2>
    <AllocationChart />
  </section>
  <section>
    <h2>Holdings</h2>
    <HoldingsTable />
  </section>
</main>
```

---

### ✅ Table structure is semantic

**Checklist:**
- [ ] Uses `<table>` element (not divs)
- [ ] Has `<thead>` for header row
- [ ] Has `<tbody>` for data rows
- [ ] Uses `<th scope="col">` for column headers
- [ ] Uses `<th scope="row">` for row headers (if applicable)
- [ ] Header row in `<thead>` (not in tbody)

**Bad:**
```tsx
<div className="table">
  <div className="row">
    <div className="cell">Asset</div>
    <div className="cell">Value</div>
  </div>
  {holdings.map(h => (
    <div className="row" key={h.id}>
      <div className="cell">{h.name}</div>
      <div className="cell">{h.value}</div>
    </div>
  ))}
</div>
```

**Good:**
```tsx
<table>
  <thead>
    <tr>
      <th scope="col">Asset</th>
      <th scope="col">Value</th>
    </tr>
  </thead>
  <tbody>
    {holdings.map(h => (
      <tr key={h.id}>
        <td>{h.name}</td>
        <td>{h.value}</td>
      </tr>
    ))}
  </tbody>
</table>
```

---

### ✅ Button vs Link semantics

**Checklist:**
- [ ] Primary actions use `<button>` (Save, Delete, Add)
- [ ] Navigation uses `<a>` (links to pages)
- [ ] Buttons have `onClick` or `type="submit"`
- [ ] Links have `href` attribute
- [ ] Links open external URLs with `target="_blank"`

**Bad:**
```tsx
<a href="#" onClick={handleSave}>Save</a>  {/* Looks like link */}
<button onClick={() => navigate('/page')}>Go</button>  {/* Is navigation */}
```

**Good:**
```tsx
<button onClick={handleSave}>Save</button>  {/* Action */}
<a href="/portfolios">View Portfolios</a>  {/* Navigation */}
```

---

### ✅ Form inputs have labels

**Checklist:**
- [ ] Every `<input>` has associated `<label>`
- [ ] Label uses `htmlFor` attribute
- [ ] Label text is visible (not hidden)
- [ ] Placeholder is supplemental, not label replacement
- [ ] Error messages are linked to input

**Bad:**
```tsx
<input type="text" placeholder="Portfolio name" />
```

**Good:**
```tsx
<div>
  <label htmlFor="portfolio-name">Portfolio Name</label>
  <input
    id="portfolio-name"
    type="text"
    placeholder="e.g., Retirement Fund"
  />
  {error && <span id="portfolio-error" role="alert">{error}</span>}
</div>
```

---

## Data Formatting Audit

### ✅ All numbers use monospace font

**Checklist:**
- [ ] Currency uses `var(--font-mono)` (IBM Plex Mono)
- [ ] Percentages use `var(--font-mono)`
- [ ] Numbers use `var(--font-mono)`
- [ ] Non-numeric text uses `var(--font-body)` (DM Sans)

**Bad:**
```tsx
<td>{value}</td>  {/* Uses body font */}
```

**Good:**
```tsx
<td style={{ fontFamily: 'var(--font-mono)' }}>
  {formatCurrency(value)}
</td>
```

---

### ✅ Currency is formatted correctly

**Checklist:**
- [ ] Uses `formatCurrency()` utility from `src/utils/formatters.ts`
- [ ] Format: `$X,XXX.XX` or `$X.XM` (abbreviated)
- [ ] Negative values: `–$X,XXX.XX` (with minus sign)
- [ ] Pairs with color (green for gains, red for losses)

**Bad:**
```tsx
<span>${value}</span>  {/* No formatting */}
<span>${-1234}</span>  {/* Bad format */}
```

**Good:**
```tsx
import { formatCurrency } from '@/utils/formatters';

<span style={{ fontFamily: 'var(--font-mono)' }}>
  {formatCurrency(1234.5)}  {/* $1,234.50 */}
</span>
```

---

### ✅ Percentages are formatted correctly

**Checklist:**
- [ ] Uses `formatPercentage()` utility
- [ ] Format: `XX.XX%`
- [ ] 2 decimal places always
- [ ] Pairs with positive (green) or negative (red) color

**Bad:**
```tsx
<span>{value * 100}%</span>  {/* 12.450000000000001% */}
```

**Good:**
```tsx
import { formatPercentage } from '@/utils/formatters';

<span>{formatPercentage(0.1245)}</span>  {/* 12.45% */}
```

---

### ✅ Dates are formatted consistently

**Checklist:**
- [ ] Uses `formatDate()` utility
- [ ] Format: `YYYY-MM-DD` (ISO 8601)
- [ ] No time component shown (dates only)
- [ ] Handles null/undefined gracefully

**Bad:**
```tsx
<td>{new Date(timestamp).toLocaleDateString()}</td>  {/* Locale-dependent */}
```

**Good:**
```tsx
import { formatDate } from '@/utils/formatters';

<td>{formatDate(timestamp)}</td>  {/* 2026-01-15 */}
```

---

### ✅ Missing data is handled correctly

**Checklist:**
- [ ] Uses `formatMissing()` utility or `??` operator
- [ ] Displays `—` (em-dash) for null/undefined
- [ ] Zero values display `0.00` (not blank)
- [ ] Long text truncated with ellipsis + tooltip

**Bad:**
```tsx
<td>{value}</td>  {/* Blank if null */}
<td>{value || 'N/A'}</td>  {/* Shows "N/A" */}
<td>{asset.description}</td>  {/* Could overflow */}
```

**Good:**
```tsx
import { formatMissing, truncateText } from '@/utils/formatters';

<td>{formatMissing(value)}</td>  {/* Shows — */}
<td>{formatZero(value, 'currency')}</td>  {/* Shows $0.00 */}
<td>
  <Tooltip content={asset.description}>
    <span>{truncateText(asset.description, 50).truncated}</span>
  </Tooltip>
</td>
```

---

### ✅ Gains/losses have correct formatting and color

**Checklist:**
- [ ] Uses `formatGainLoss()` utility
- [ ] Positive values: green (`var(--pb-success)`) with `+` sign
- [ ] Negative values: red (`var(--pb-danger)`) with `–` sign
- [ ] Pairs with icon (up, down, dash)
- [ ] Uses monospace font

**Bad:**
```tsx
<span style={{ color: value > 0 ? 'green' : 'red' }}>
  {value}  {/* No formatting, no icon */}
</span>
```

**Good:**
```tsx
import { formatGainLoss, getSemanticColor } from '@/utils/formatters';

const { color, icon } = getSemanticColor(value);
const iconMap = { up: <TrendUp />, down: <TrendDown />, dash: <Dash /> };

<span style={{ fontFamily: 'var(--font-mono)', color, display: 'flex', gap: '4px' }}>
  {iconMap[icon]}
  {formatGainLoss(value, 'currency')}  {/* +$5,000.00 */}
</span>
```

---

## Color & Accessibility Audit

### ✅ Colors use design tokens

**Checklist:**
- [ ] All colors use `--pb-*` CSS variables (no hardcoded hex)
- [ ] No custom color values
- [ ] Text colors from `--pb-text-*` family
- [ ] Background colors from `--pb-bg`, `--pb-surface`, `--pb-surface-elevated`
- [ ] Semantic colors: success, warning, danger, accent

**Bad:**
```tsx
style={{ color: '#EF4444', backgroundColor: '#0E1420' }}
```

**Good:**
```tsx
style={{ color: 'var(--pb-danger)', backgroundColor: 'var(--pb-surface)' }}
```

---

### ✅ Color signals paired with icons/text

**Checklist:**
- [ ] No isolated color signals (never use color alone)
- [ ] Success states: green icon + text
- [ ] Error states: red icon + text
- [ ] Warning states: amber icon + text
- [ ] Info states: blue icon + text

**Bad:**
```tsx
<div style={{ color: '#10B981' }}>●</div>  {/* Color only */}
```

**Good:**
```tsx
<div style={{ display: 'flex', gap: '8px', color: 'var(--pb-success)' }}>
  <CheckmarkIcon />
  <span>Portfolio saved</span>
</div>
```

---

### ✅ Keyboard navigation works

**Checklist:**
- [ ] All interactive elements are focusable (button, link, input, etc.)
- [ ] Focus order is logical (top-to-bottom, left-to-right)
- [ ] Tab key navigates forward
- [ ] Shift+Tab navigates backward
- [ ] Enter activates buttons/links
- [ ] Escape closes modals/menus
- [ ] Focus visible state is clear (outline or background)

**Test manually:**
```
1. Tab through page — all interactive elements should be reachable
2. Shift+Tab — should go backward
3. Enter — should activate focused button/link
4. Escape — should close modal/dropdown
5. Focus outline — should be clearly visible
```

---

### ✅ Tooltips are accessible

**Checklist:**
- [ ] Trigger element is focusable (button or tabindex="0")
- [ ] Tooltip appears on hover AND focus
- [ ] Uses `aria-describedby` to link tooltip to trigger
- [ ] Tooltip content is readable
- [ ] Appears with slight delay (~150ms) to avoid flashing

**Bad:**
```tsx
<span style={{ cursor: 'pointer' }} onMouseEnter={showTooltip}>
  Info
  {showTooltip && <div>{tooltipText}</div>}
</span>
```

**Good:**
```tsx
<Tooltip content={tooltipText}>
  <button aria-describedby="info-tooltip">
    <InfoIcon />
  </button>
</Tooltip>
```

---

### ✅ Color contrast meets WCAG AA

**Checklist:**
- [ ] Text on dark backgrounds: light text (`--pb-text-primary`)
- [ ] Contrast ratio: 4.5:1 for normal text, 3:1 for large text
- [ ] No pure white on dark (use off-white `#F1F5F9`)
- [ ] Test with contrast checker tool (https://webaim.org/resources/contrastchecker/)

**Test:**
```
Text: #F1F5F9 on #0A0D12 ✅ (21:1 ratio - excellent)
Text: #64748B on #0E1420 ✅ (8.5:1 ratio - good)
Text: #334155 on #0A0D12 ❌ (2.5:1 ratio - fail)
```

---

## Component Reuse Audit

### ✅ No custom component rebuilds

**Checklist:**
- [ ] Empty states use `EmptyState` component (not custom)
- [ ] Loading states use `SkeletonLoader` component (not spinners)
- [ ] Buttons use `ProbityButton` component (not custom styling)
- [ ] Tables use `DataTable` component (or semantic `<table>`)
- [ ] Cards use `StatCard` or `PortfolioCard` (not custom)
- [ ] Modals use `Modal` component (not custom overlays)
- [ ] Tooltips use `Tooltip` component (not custom popovers)

**Audit command:**
```bash
# Search for hardcoded component reimplementations
grep -r "backgroundColor.*surface" src/components/
grep -r "border.*dashed" src/components/  # Look for custom empty states
grep -r "Spinner" src/components/        # Look for custom loaders
```

---

## Micro-interactions Audit

### ✅ Animations are <200ms

**Checklist:**
- [ ] Button clicks: instant feedback
- [ ] Hover transitions: 100–150ms
- [ ] Modal open/close: <200ms
- [ ] Drawer slide: <200ms
- [ ] Skeleton shimmer: 800–1200ms (slow intentional)
- [ ] No animation lag or jank

**Test:**
```tsx
// Good: Fast transition
style={{ transition: '150ms ease-in-out' }}

// Bad: Slow or janky
style={{ transition: '500ms ease-in-out' }}
```

---

### ✅ Hover states are subtle

**Checklist:**
- [ ] Table row hover: background shift to `--pb-surface-elevated`
- [ ] Button hover: background brightens or shadow appears
- [ ] Link hover: underline appears or text color lightens
- [ ] No aggressive color changes
- [ ] Cursor changes to pointer on interactive elements

**Test:**
Hover over interactive elements — subtle, not jarring.

---

### ✅ Button click has instant feedback

**Checklist:**
- [ ] Pressed state visible immediately on click
- [ ] Button disabled while processing
- [ ] Loading indicator shows inside button
- [ ] Button text changes (e.g., "Saving…")

**Test:**
```tsx
<ProbityButton 
  onClick={handleSave}
  isLoading={isSaving}
  disabled={isSaving}
>
  {isSaving ? 'Saving…' : 'Save'}
</ProbityButton>
```

---

## Consistency Audit

### ✅ Same state = same UI everywhere

**Checklist:**
- [ ] Empty state component identical across all pages
- [ ] Loading skeleton looks the same everywhere
- [ ] Error messages styled consistently
- [ ] Toast notifications always top-right
- [ ] Button styles consistent (colors, sizes, spacing)
- [ ] Form inputs styled consistently

**Audit:**
```bash
# Compare empty state implementations
grep -r "EmptyState" src/pages/
# Should see only <EmptyState /> usage, not custom implementations

# Check for inline error styling
grep -r "backgroundColor.*rgba.*68.*68" src/pages/
# Should see consistent danger color usage
```

---

### ✅ Action placement is consistent

**Checklist:**
- [ ] Add/Create actions: top-right of section
- [ ] Edit actions: inline or dropdown menu
- [ ] Delete actions: dropdown menu (danger variant)
- [ ] Export: top-right or in dropdown
- [ ] Save/Confirm: right-aligned in modal
- [ ] Cancel: left of primary action

**Audit layout:**
```
┌─ Portfolio Holdings ─────────── [Add Asset] ✅
├─────────────────────────────────────────────
│ Row 1 [Edit ▼] ✅
│ Row 2 [Edit ▼] ✅
└─────────────────────────────────────────────

[Cancel]  [Save] ✅
```

---

### ✅ Severity = color + icon consistency

**Checklist:**
- [ ] Success: green + checkmark everywhere
- [ ] Error: red + alert icon everywhere
- [ ] Warning: amber + warning icon everywhere
- [ ] Info: blue + info circle everywhere
- [ ] No inconsistent color usage (e.g., orange for success)

**Audit:**
```bash
# Check for success color consistency
grep -r "pb-success" src/components/
# Should see consistent usage across toasts, badges, icons

# Check for danger color consistency
grep -r "pb-danger" src/components/
# Should see consistent usage in errors, warnings, deletions
```

---

## Mobile & Responsive Audit

### ✅ Touch-friendly spacing

**Checklist:**
- [ ] All tap targets are 44×44px minimum
- [ ] Buttons and links: padding supports 44px size
- [ ] Forms: input height ≥ 44px
- [ ] Table rows clickable area: full row height ≥ 44px
- [ ] No elements <44px without padding

---

### ✅ Content reflows on mobile

**Checklist:**
- [ ] Layout adapts to smaller screens
- [ ] No horizontal scrolling
- [ ] Tables either scroll horizontally OR collapse to card view
- [ ] Sidebar becomes hamburger menu (or collapses)
- [ ] Modals are full-screen or nearly full on mobile

**Test:**
```bash
# Test responsive breakpoints
Desktop (1920px): Full layout
Tablet (768px): Sidebar collapsed, content stacked
Mobile (375px): Hamburger menu, single column
```

---

### ✅ No hover-only interactions

**Checklist:**
- [ ] Tooltips work on touch (focus or tap)
- [ ] Dropdowns work on touch (not hover-only)
- [ ] Context menus accessible without hover
- [ ] Interactive elements don't require hover

---

## Audit Report Template

Use this template to document audit findings:

```markdown
# Component Audit: [Component Name]

**Date:** 2026-01-15  
**Auditor:** [Name]  
**Component Path:** `src/components/[path]`

## Data States
- [ ] Loading state: ✅ / ⚠️ / ❌
- [ ] Empty state: ✅ / ⚠️ / ❌
- [ ] Error state: ✅ / ⚠️ / ❌
- [ ] Partial data: ✅ / ⚠️ / ❌

**Notes:**

## Semantic HTML
- [ ] Headings: ✅ / ⚠️ / ❌
- [ ] Tables: ✅ / ⚠️ / ❌
- [ ] Forms: ✅ / ⚠️ / ❌
- [ ] Buttons/Links: ✅ / ⚠️ / ❌

**Notes:**

## Data Formatting
- [ ] Numbers: ✅ / ⚠️ / ❌
- [ ] Currency: ✅ / ⚠️ / ❌
- [ ] Dates: ✅ / ⚠️ / ❌
- [ ] Missing data: ✅ / ⚠️ / ❌

**Notes:**

## Colors & Accessibility
- [ ] Design tokens: ✅ / ⚠️ / ❌
- [ ] Keyboard nav: ✅ / ⚠️ / ❌
- [ ] Contrast: ✅ / ⚠️ / ❌
- [ ] Color + signal: ✅ / ⚠️ / ❌

**Notes:**

## Component Reuse
- [ ] No custom rebuilds: ✅ / ⚠️ / ❌
- [ ] Uses existing components: ✅ / ⚠️ / ❌

**Notes:**

## Micro-interactions
- [ ] Animations <200ms: ✅ / ⚠️ / ❌
- [ ] Hover states: ✅ / ⚠️ / ❌
- [ ] Loading feedback: ✅ / ⚠️ / ❌

**Notes:**

## Consistency
- [ ] Same state = same UI: ✅ / ⚠️ / ❌
- [ ] Action placement: ✅ / ⚠️ / ❌
- [ ] Severity colors: ✅ / ⚠️ / ❌

**Notes:**

## Mobile & Responsive
- [ ] Touch targets 44×44px: ✅ / ⚠️ / ❌
- [ ] Content reflows: ✅ / ⚠️ / ❌
- [ ] No hover-only interactions: ✅ / ⚠️ / ❌

**Notes:**

## Summary
**Overall Status:** ✅ Compliant / ⚠️ Minor Issues / ❌ Major Issues

**Recommended Actions:**
1. [Action 1]
2. [Action 2]

**Priority:** High / Medium / Low

---
**Signed off by:** [Name], [Date]
```

---

## Running Automated Audits

```bash
# Type checking (catches some semantic issues)
npm run type-check

# Linting (catches code quality issues)
npm run lint

# Accessibility testing (with axe or similar)
npm run test:a11y

# Visual regression testing (catch unintended changes)
npm run test:visual
```

---

**Questions?** Refer to [Global Behavior Rules](./GLOBAL_BEHAVIOR_RULES.md) or [Implementation Guide](./IMPLEMENTATION_GUIDE.md)
