# Probity Design System: Global Behavior Rules

**Last Updated:** 2026  
**Status:** Living Document  
**Version:** 1.0

---

## Overview

This document establishes the unified behavioral patterns for all screens and components across the Probity platform. It ensures consistency, clarity, semantic correctness, and a predictable user experience when handling real-world data conditions.

Every data-driven component, screen, and interaction must conform to these rules.

---

## 1. Data States (Required for Every Data Component)

Every component that displays data must explicitly support **4 distinct states**, appearing in this sequence:

### 1.1 Loading State (Skeleton First)

**When to use:** During initial data fetch or refresh operations.

**Rules:**
- Use `SkeletonLoader` component — never spinners as primary loading UI
- Skeleton layout must match the exact shape/dimensions of the final component
  - Tables: show 5–8 skeleton rows with column widths matching header
  - Cards: match card height and content block spacing
  - Charts: show placeholder grid lines and a faded line structure
- Animate with a subtle shimmer effect (left-to-right, 800–1200ms duration)
- Content must load without layout shift (reserve space with skeleton)
- Sidebar and navigation remain fully interactive during loading

**Implementation reference:**
- Component: `src/components/probity/SkeletonLoader.tsx`
- Token colors:
  - Base: `var(--pb-surface-elevated)` (#131B2E)
  - Shimmer: gradient from `var(--pb-border)` to transparent

**Example:**
```tsx
// Always wrap loading state with skeleton
{isLoading ? (
  <SkeletonLoader type="table" rows={8} />
) : (
  <DataTable data={data} />
)}
```

---

### 1.2 Empty State (No Data)

**When to use:** When a data source returns zero results and is not due to an error.

**Requirements:**
- Use the `EmptyState` component (reusable across all screens)
- Container styling:
  - Dashed border: `1px dashed var(--pb-border)` (#1E2D45)
  - Background: `rgba(14,20,32,0.4)` (subtle overlay on surface)
  - Rounded corners: `8px`
  - Min height: match surrounding container or 200px minimum
- Content structure:
  - Icon (non-decorative, functional — e.g., briefcase for empty portfolio, chart for no data)
  - Heading: Clear, action-oriented message (use `<h3>` with Syne font)
  - Description: 1–2 lines of instructional microcopy (use DM Sans, secondary text color)
  - Primary CTA: action button when applicable (bottom of empty state)

**Required message patterns:**
```
Heading: "[Entity] not found" or "No [entities]"
Description: "[Action needed] to see [entity type]"
Button: "Add [entity]" or "Create [entity]"
```

**Example:**
```
Heading: "No positions found"
Description: "Add assets to start analyzing portfolio risk"
Button: "Add Asset"
```

**Implementation reference:**
- Component: `src/components/probity/EmptyState.tsx`
- Do NOT create custom empty states per page — always reuse this component
- Styling uses CSS variables and `pb-*` tokens

**Code example:**
```tsx
<EmptyState
  icon={<BriefcaseIcon />}
  title="No positions found"
  description="Add assets to start analyzing portfolio risk"
  actionLabel="Add Asset"
  onAction={() => openAddAssetDrawer()}
/>
```

---

### 1.3 Error State

**When to use:** When data fetch fails due to network, server, or permission issues.

**Requirements:**
- Display inline (not full-page overlay) unless critical system failure
- Container styling:
  - Border: `1px solid var(--pb-danger)` (#EF4444)
  - Background: `rgba(239,68,68,0.08)` (subtle danger tint)
  - Rounded corners: `8px`
  - Padding: `16px`
  - Icon: warning icon (triangle or alert symbol)
- Message requirements:
  - **Must be human-readable** — never expose system error codes or stack traces
  - Explain what failed in plain language
  - Suggest next step (retry, contact support)
- Include a **Retry action** when possible (button or link)
- Position: above affected content or inline with card

**Required message pattern:**
```
"[Action] failed. [Next step]"
```

**Examples:**
```
"Failed to load portfolio data. Try again."
"Unable to fetch market prices. Refresh in a moment."
"You don't have permission to view this portfolio. Contact your admin."
```

**Implementation reference:**
- Create error UI using base `Alert` component with danger variant
- Token colors:
  - Border: `var(--pb-danger)` (#EF4444)
  - Background: Mix of danger + surface
  - Text: `var(--pb-text-primary)` for message, icon color danger

**Code example:**
```tsx
{error && (
  <div style={{
    border: '1px solid var(--pb-danger)',
    backgroundColor: 'rgba(239,68,68,0.08)',
    borderRadius: '8px',
    padding: '16px',
    display: 'flex',
    gap: '12px',
  }}>
    <AlertIcon color="var(--pb-danger)" />
    <div>
      <p style={{ color: 'var(--pb-text-primary)', fontWeight: '600' }}>
        Failed to load portfolio data
      </p>
      <p style={{ color: 'var(--pb-text-secondary)', fontSize: '14px' }}>
        Try again or contact support if the problem persists.
      </p>
      <button onClick={retry}>Try Again</button>
    </div>
  </div>
)}
```

---

### 1.4 Partial Data State

**When to use:** Some data loads successfully, but other sections fail or are missing.

**Rules:**
- Render all successfully loaded data normally
- For missing sections:
  - Show inline warning banner (yellow/info color)
  - Use muted styling (secondary text color)
  - Never block the entire UI for partial failures
- Message pattern: "[Section] data is not available right now. [What works]."

**Example:**
```
Portfolio data loaded, but performance history is unavailable.
Showing current positions and risk breakdown.
[Refresh button]
```

**Implementation:**
- Use inline info banner above affected section
- Colors:
  - Border: `var(--pb-warning)` (#F59E0B)
  - Background: `rgba(245,158,11,0.08)`
  - Text: `var(--pb-text-secondary)`

---

## 2. Semantic Structure (Accessibility + Clarity)

### 2.1 HTML Hierarchy

**Heading structure (required on every page):**
- **One `<h1>` per page** — typically the page title (use Syne font)
  - Example: Dashboard, Portfolio: "Acme Corp Portfolio"
- **`<h2>` for major sections** (use Syne font)
  - Example: "Asset Allocation", "Performance Overview"
- **`<h3>` for subsections** (use Syne font)
  - Example: Empty states, card titles, section groupings
- Never skip heading levels (don't jump from `<h1>` to `<h3>`)

**Token reference:**
```css
h1 { font-family: var(--font-display); font-size: 32px; font-weight: 700; }
h2 { font-family: var(--font-display); font-size: 24px; font-weight: 700; }
h3 { font-family: var(--font-display); font-size: 20px; font-weight: 700; }
```

---

### 2.2 Table Semantic Structure

**Required markup:**
```tsx
<table>
  <thead>
    <tr>
      <th scope="col">Column Name</th>
      {/* ... */}
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Data</td>
      {/* ... */}
    </tr>
  </tbody>
</table>
```

**Rules:**
- Use `<thead>` for header row, `<tbody>` for data rows
- Use `<th scope="col">` for column headers
- Use `<th scope="row">` for row headers (if applicable)
- Reference component: `src/components/ui/table.tsx` and `src/components/probity/DataTable.tsx`

---

### 2.3 Button vs. Link Semantics

| Element | When to Use |
|---------|------------|
| `<button>` | Primary actions (Save, Delete, Add, Submit) |
| `<a>` | Navigation (Link to page, external URL) |
| `<a role="button">` | Link that triggers an action (only if unavoidable) |

**Code pattern:**
```tsx
// Primary action → button
<button onClick={handleSave}>Save</button>

// Navigate → link
<a href="/portfolios">View All Portfolios</a>

// Navigate to external → link with target="_blank"
<a href="https://example.com" target="_blank">Help Center</a>
```

---

### 2.4 Input & Label Requirements

**Every input must have an associated label:**
```tsx
<div>
  <label htmlFor="portfolio-name">Portfolio Name</label>
  <input id="portfolio-name" type="text" placeholder="e.g., Retirement Fund" />
</div>
```

**Rules:**
- Never rely on placeholder text alone as a label
- Use `<label>` with `htmlFor` attribute or wrap input in label
- Reference component: `src/components/probity/InputField.tsx`
- Label text: use DM Sans, secondary text color

---

### 2.5 Tooltip Accessibility

**Requirements:**
- Tooltips must be accessible via both mouse hover and keyboard focus
- Trigger elements must be focusable (buttons, links, or `tabindex="0"`)
- Tooltip must appear with slight delay (~150ms) to avoid flashing
- Use `aria-describedby` to link tooltip to trigger element

**Code pattern:**
```tsx
<Tooltip content="This is help text">
  <button aria-describedby="help-tooltip">
    Hover for help
  </button>
</Tooltip>
```

**Reference component:** `src/components/probity/Tooltip.tsx`

---

### 2.6 Color + Non-Color Signals

**Rule: Color is never the only signal.**

Every colored message must pair with:
- **Icon** (semantically meaningful)
- **Text label** (explicit message)
- **Badge or badge-like styling** (optional)

**Examples:**

| State | Color | Icon | Text |
|-------|-------|------|------|
| Success | Green (`var(--pb-success)`) | Checkmark | "Position added" |
| Error | Red (`var(--pb-danger)`) | Alert triangle | "Failed to load" |
| Warning | Amber (`var(--pb-warning)`) | Warning icon | "Data refreshing" |
| Info | Blue (`var(--pb-accent)`) | Info circle | "3 assets added" |

**Bad example:**
```tsx
// Don't do this — color alone
<span style={{ color: '#EF4444' }}>●</span>
```

**Good example:**
```tsx
// Color + icon + text
<div style={{ display: 'flex', gap: '8px', color: 'var(--pb-success)' }}>
  <CheckmarkIcon />
  <span>Position added</span>
</div>
```

---

## 3. Loading Strategy (Perceived Performance)

### 3.1 Content Prioritization

**Load priority order:**
1. Page layout and navigation (always first)
2. Above-the-fold content (summary stats, key metrics)
3. Detail sections (tables, charts, lists)
4. Optional/supplementary content (related items, analytics)

**Example on dashboard:**
```
1. [LOAD IMMEDIATELY]
   - Page header, sidebar, top navigation
   
2. [LOAD NEXT] 
   - Key metric cards (portfolio value, return %)
   - Asset allocation summary
   
3. [LOAD AFTER]
   - Detailed holdings table
   - Performance chart
   - Risk breakdown
```

---

### 3.2 Progressive Rendering

**Strategy:**
- Show layout (skeleton) immediately with reserved space
- Replace sections one at a time as data arrives
- Never block entire UI for one slow request
- Sidebar, navigation, and primary CTAs always remain interactive

**Implementation pattern:**
```tsx
export function Dashboard() {
  const [metrics, setMetrics] = useState(null);
  const [holdings, setHoldings] = useState(null);
  const [chart, setChart] = useState(null);

  useEffect(() => {
    // Load metrics first (critical path)
    fetchMetrics().then(setMetrics);
    
    // Then load holdings (secondary path)
    fetchHoldings().then(setHoldings);
    
    // Finally, load chart (tertiary path)
    fetchChart().then(setChart);
  }, []);

  return (
    <>
      {/* Always visible */}
      <Sidebar />
      <TopNav />
      
      {/* Progressive sections */}
      {metrics ? <MetricsCards /> : <SkeletonLoader type="card" count={3} />}
      {holdings ? <HoldingsTable /> : <SkeletonLoader type="table" />}
      {chart ? <PerformanceChart /> : <SkeletonLoader type="chart" />}
    </>
  );
}
```

---

### 3.3 Data Caching & Stale Data

**Strategy:**
- Cache last known data in state
- Show stale data while refreshing (optional "Updating…" label)
- Never leave user staring at blank screen

**Implementation pattern:**
```tsx
const [holdings, setHoldings] = useState(cachedHoldings);
const [isRefreshing, setIsRefreshing] = useState(false);

const refresh = async () => {
  setIsRefreshing(true);
  const newData = await fetchHoldings();
  setHoldings(newData);
  setIsRefreshing(false);
};

return (
  <>
    {isRefreshing && <InfoBanner>Updating holdings…</InfoBanner>}
    <HoldingsTable data={holdings} />
  </>
);
```

---

## 4. Micro-Interactions

### 4.1 Hover States

**Button hover:**
- Subtle background shift to lighter shade
- Shadow optional but not aggressive
- Transition: 150ms ease-in-out

**Table row hover:**
- Background: `var(--pb-surface-elevated)` (#131B2E)
- Transition: 100ms ease-in-out
- Cursor: pointer

**Link hover:**
- Underline appears or strengthens
- Color lightens slightly
- No background change

**Reference color:** `var(--pb-surface-elevated)` used for hover states

---

### 4.2 Button Click Feedback

**Rules:**
- Instant visual feedback on click (pressed state)
- Button remains in pressed state during submission
- Disable button while processing (prevent double-clicks)
- Show spinner or loading state inside button if action is >500ms

**Code pattern:**
```tsx
<ProbityButton
  onClick={handleSubmit}
  isLoading={isSubmitting}
  disabled={isSubmitting}
>
  {isSubmitting ? 'Saving…' : 'Save'}
</ProbityButton>
```

**Reference component:** `src/components/probity/ProbityButton.tsx`

---

### 4.3 Table Row Selection

**Interaction pattern:**
- Highlight row on hover
- Show selection checkbox on row
- Selected state persists (background + checkmark)
- Allow multi-select with Shift/Ctrl+Click

**Colors:**
- Hover: `var(--pb-surface-elevated)` (#131B2E)
- Selected: Slightly lighter with border accent

---

### 4.4 Modal & Drawer Animations

**Rules:**
- Fade + slight slide animation
- Duration: <200ms for snappy feel
- Easing: ease-in-out cubic-bezier
- Enter from: top (modals), right (drawers)
- Exit: reverse animation

**Code pattern (CSS):**
```css
@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal {
  animation: modalSlideIn 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
```

**Reference components:**
- `src/components/probity/Modal.tsx`
- `src/components/ui/dialog.tsx` (base primitive)
- `src/components/ui/sheet.tsx` (drawer variant)

---

### 4.5 Tooltip Animations

**Rules:**
- Appear with 150ms delay (avoid flashing on quick hover)
- Fade in smoothly
- Position: prefer top/bottom, fallback to left/right
- Max width: 200px
- Font: DM Sans, 12px, secondary text color

**Reference component:** `src/components/probity/Tooltip.tsx`

---

## 5. Feedback & System Status

### 5.1 Toast Notifications

**Position:** Top-right corner, 16px from edge

**Rules by type:**

| Type | Color | Icon | Duration | Auto-dismiss |
|------|-------|------|----------|--------------|
| Success | `var(--pb-success)` (#10B981) | Checkmark | 4s | Yes |
| Error | `var(--pb-danger)` (#EF4444) | Alert | 6s | No (user must close) |
| Info | `var(--pb-accent)` (#3B82F6) | Info circle | 4s | Yes |
| Warning | `var(--pb-warning)` (#F59E0B) | Warning | 5s | Yes |

**Message requirements:**
- **Success:** "[Action] completed" or "[Entity] [verb]ed successfully"
  - Examples: "Portfolio created", "Position added", "Data saved"
- **Error:** "[Action] failed. [Suggestion]"
  - Examples: "Failed to delete portfolio. Try again.", "Network error. Check your connection."
- **Info:** "[Context] about [action or state]"
  - Examples: "3 assets added to portfolio", "Data refreshed"

**Code pattern:**
```tsx
// Success toast
showToast({
  type: 'success',
  message: 'Portfolio created successfully',
});

// Error toast (stays until dismissed)
showToast({
  type: 'error',
  message: 'Failed to load portfolio data. Try again.',
  dismissible: true,
});
```

---

### 5.2 Long-Running Actions

**For actions >500ms:**
- Show loading state inside button or field (spinner or progress)
- Disable button to prevent double-click
- Show inline progress indicator next to action
- Optional: show estimated time remaining

**Code pattern:**
```tsx
const [isLoading, setIsLoading] = useState(false);

async function handleExport() {
  setIsLoading(true);
  try {
    await exportPortfolio();
    showToast({ type: 'success', message: 'Export ready' });
  } finally {
    setIsLoading(false);
  }
}

return (
  <ProbityButton 
    onClick={handleExport} 
    isLoading={isLoading}
    disabled={isLoading}
  >
    {isLoading ? 'Exporting…' : 'Export'}
  </ProbityButton>
);
```

---

## 6. Data Formatting Rules

All numeric and temporal data must follow strict formatting rules for consistency and clarity.

### 6.1 Typography for Numbers

**Font:** IBM Plex Mono (monospace)
- All currency, percentages, numbers use `var(--font-mono)`
- Ensures alignment and readability in tables/lists

---

### 6.2 Currency Format

**Format:** `$[number with comma separators and 2 decimals]`

| Value | Display |
|-------|---------|
| 1234.5 | $1,234.50 |
| 1000000 | $1,000,000.00 |
| 0.99 | $0.99 |
| 1200000 | $1.2M (if space-constrained) |

**Code pattern:**
```tsx
const formatCurrency = (value) => {
  if (Math.abs(value) >= 1000000) {
    return `$${(value / 1000000).toFixed(1)}M`;
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(value);
};
```

---

### 6.3 Percentage Format

**Format:** `[number].[2 decimals]%`

| Value | Display |
|-------|---------|
| 0.1245 | 12.45% |
| 0.025 | 2.50% |
| 0.0 | 0.00% |

**Code pattern:**
```tsx
const formatPercentage = (value) => {
  return (value * 100).toFixed(2) + '%';
};
```

---

### 6.4 Date Format

**Format:** `YYYY-MM-DD`

| Value | Display |
|-------|---------|
| Jan 15, 2026 | 2026-01-15 |
| Today | 2026-04-19 |

**Code pattern:**
```tsx
const formatDate = (date) => {
  return date.toISOString().split('T')[0]; // YYYY-MM-DD
};
```

---

### 6.5 Negative Values

**Rules:**
- Color: Red (`var(--pb-danger)` #EF4444)
- Always include minus sign (–)
- Font: IBM Plex Mono

**Example:**
```tsx
<span style={{ 
  color: 'var(--pb-danger)', 
  fontFamily: 'var(--font-mono)' 
}}>
  –$2,450.32
</span>
```

---

### 6.6 Positive Values (Returns/Gains)

**Rules:**
- Color: Green (`var(--pb-success)` #10B981) — **only when semantically meaningful** (e.g., returns, gains)
- Do NOT color positive currency amounts in neutral contexts
- Include plus sign (+) if emphasizing growth
- Font: IBM Plex Mono

**When to color positive green:**
- Portfolio return: +12.45% ✅ (green)
- Unrealized gain: +$5,000.00 ✅ (green)
- Account balance: $50,000.00 ❌ (no color)

**Example:**
```tsx
<span style={{ 
  color: 'var(--pb-success)', 
  fontFamily: 'var(--font-mono)' 
}}>
  +$5,000.00
</span>
```

---

## 7. Edge Cases (Always Handle)

### 7.1 Zero Values

**Rule: Display "0.00", never blank or null**

| Type | Display |
|------|---------|
| Currency | $0.00 |
| Percentage | 0.00% |
| Integer | 0 |

**Reasoning:** Distinguishes between "no data" and "data with zero value"

**Code pattern:**
```tsx
const displayValue = value ?? 0; // Never null/undefined
```

---

### 7.2 Extremely Large Values

**Rule: Abbreviate to keep UI compact**

| Value | Display | Use Case |
|-------|---------|----------|
| 1,200,000 | $1.2M | Currency in cards/summary |
| 1,234,567 | $1,234,567 | Currency in detailed tables |
| 1,234,567,890 | $1.2B | Currency for very large values |

**Code pattern:**
```tsx
const formatLargeNumber = (value, decimals = 1) => {
  if (Math.abs(value) >= 1000000000) {
    return `$${(value / 1000000000).toFixed(decimals)}B`;
  }
  if (Math.abs(value) >= 1000000) {
    return `$${(value / 1000000).toFixed(decimals)}M`;
  }
  if (Math.abs(value) >= 1000) {
    return `$${(value / 1000).toFixed(decimals)}K`;
  }
  return `$${value.toFixed(decimals)}`;
};
```

---

### 7.3 Missing Data

**Rule: Use em-dash "—" never null/undefined/N/A**

**When to use:**
- Data not yet available
- Data not applicable for this row/context
- Data temporarily unavailable (do not use empty state — use dash in cell)

**Example table:**
```
Asset        | Purchase Price | Current Price | Gain/Loss
Acme Stock   | $50.00         | $75.50        | +$25.50
XYZ Bond     | —              | —             | —
Old Holdings | $100.00        | —             | —
```

**Code pattern:**
```tsx
<td style={{ 
  fontFamily: 'var(--font-mono)',
  color: 'var(--pb-text-muted)',
}}>
  {data.price ?? '—'}
</td>
```

---

### 7.4 Long Text

**Rule: Truncate with ellipsis + tooltip on hover**

**Truncation points:**
- Portfolio names: 20 characters
- Asset symbols: 10 characters
- Asset descriptions: 50 characters
- Notes/comments: 100 characters

**Code pattern:**
```tsx
const truncateText = (text, maxLength) => {
  if (text.length > maxLength) {
    return text.substring(0, maxLength) + '…';
  }
  return text;
};

<Tooltip content={fullText}>
  <span>{truncateText(fullText, 30)}</span>
</Tooltip>
```

---

## 8. Consistency Rules

### 8.1 Same State = Same UI Everywhere

**Principle:** If a state looks different across screens, it's a bug.

**Example consistency checks:**
- Empty state component must be identical on dashboard, portfolios, and holdings
- Loading skeleton for tables must match across all pages
- Error messages must have consistent styling and placement
- Toast notifications always appear top-right

**Enforcement:**
- Reuse components (never rebuild)
- Use shared CSS classes from `theme.css`
- Document exceptions in component comments

---

### 8.2 Component Reuse (No Custom Variants)

**Rule: Reuse, don't rebuild.**

| Component | Where It Must Be Used | Avoid |
|-----------|----------------------|-------|
| `EmptyState` | Every empty data container | Custom empty designs |
| `SkeletonLoader` | Every loading state | Spinners, custom loaders |
| `DataTable` | All data tables | Per-page custom tables |
| `StatCard` | All metric summaries | Custom stat cards |
| `ProbityButton` | All actions | Custom button styles |
| `Modal` | All dialogs | Custom modal overlays |
| `Tooltip` | Help text, hover info | Custom tooltips |

**Why:** Ensures consistent behavior, styling, and accessibility.

---

### 8.3 Action Placement (Consistency)

**Primary actions always appear in the same location:**

| Action | Placement |
|--------|-----------|
| Add/Create | Top-right of section |
| Edit | Inline, row-end or dropdown menu |
| Delete | Dropdown menu (danger variant) |
| Export | Top-right near Add (or in dropdown) |
| Cancel | Left of primary action in modal |
| Save/Confirm | Right-aligned, primary button |
| Close/Back | Top-left (modal) or breadcrumb |

**Example layout:**
```
┌─────────────────────────────────────────┐
│ Holdings                    [Add Asset] │
├─────────────────────────────────────────┤
│ [Table Content]                         │
│ Row 1 [Edit] [Menu ▼]                   │
│ Row 2 [Edit] [Menu ▼]                   │
└─────────────────────────────────────────┘
```

---

### 8.4 Severity = Same Color + Style

**Rule: Use consistent color-severity mapping across all UI.**

| Severity | Color | Icon | Usage |
|----------|-------|------|-------|
| Success | `var(--pb-success)` | ✓ Checkmark | Completed actions, positive states |
| Info | `var(--pb-accent)` | ⓘ Info circle | Neutral information, tooltips |
| Warning | `var(--pb-warning)` | ⚠ Triangle | Caution, data refreshing, risk alerts |
| Danger/Error | `var(--pb-danger)` | ⛔ Alert | Failed actions, validation errors, risks |

**Bad example (inconsistent):**
```
Toast: "Data saved" — green text (success) ✓
Modal: "Data saved" — blue background (info) ✗
Badge: "Saved" — gray text (neutral) ✗
```

**Good example (consistent):**
```
Toast: green icon + "Data saved" text
Modal heading: "Success" with green accent border
Success badge: green background, checkmark icon
```

---

## 9. Accessibility Checklist

Every screen and component must meet these baseline accessibility requirements:

### 9.1 Keyboard Navigation
- [ ] All interactive elements are keyboard-focusable
- [ ] Focus order is logical (top-to-bottom, left-to-right)
- [ ] Tab key moves forward, Shift+Tab moves backward
- [ ] Modals trap focus (cannot tab outside)
- [ ] Escape key closes modals/dropdowns

### 9.2 Screen Reader Support
- [ ] Page has one `<h1>` with meaningful title
- [ ] Headings are properly nested (`<h1>` → `<h2>` → `<h3>`)
- [ ] Images have `alt` text (or `alt=""` if decorative)
- [ ] Links have descriptive text (not "click here")
- [ ] Buttons have text labels (not just icons)
- [ ] Form inputs have associated `<label>` elements
- [ ] ARIA roles/attributes used only when necessary (semantic HTML first)

### 9.3 Color Contrast
- [ ] All text meets WCAG AA contrast (4.5:1 for normal, 3:1 for large)
- [ ] Use `--pb-text-primary` (#F1F5F9) on dark backgrounds
- [ ] Avoid pure white on dark backgrounds (use off-white)
- [ ] Test with contrast checker tools

### 9.4 Mobile & Responsive
- [ ] All tap targets are 44×44px minimum
- [ ] Content reflows on smaller screens
- [ ] Touch-friendly spacing (no hover-only interactions)
- [ ] Test on mobile devices and with touch

---

## 10. Implementation Checklist

Use this checklist when building new features:

### Before Development
- [ ] Define the 4 data states (loading, empty, error, partial)
- [ ] Plan skeleton loaders with exact layout match
- [ ] Plan error messages (human-readable, actionable)
- [ ] Plan empty state content and primary CTA

### During Development
- [ ] Use `theme.css` design tokens (never hardcoded colors)
- [ ] Reuse components from `src/components/probity/`
- [ ] Follow semantic HTML structure (h1, h2, h3, tables, etc.)
- [ ] Add keyboard navigation support
- [ ] Test focus states and tab order
- [ ] Format numbers with IBM Plex Mono
- [ ] Format currencies, percentages, dates per rules above
- [ ] Handle edge cases (zero, missing data, large values)

### Before Shipping
- [ ] All data states working (loading, empty, error, partial)
- [ ] Tested on mobile (responsive, touch-friendly)
- [ ] All tooltips accessible (hover + focus)
- [ ] Color signals paired with icons/text
- [ ] Consistent with other screens (reused components)
- [ ] Accessibility checklist passed
- [ ] Toast notifications tested
- [ ] Error messages human-readable
- [ ] Micro-interactions smooth (<200ms)

---

## 11. Color Reference

All color values for quick reference:

```css
:root {
  /* Backgrounds */
  --pb-bg:               #0A0D12;      /* Page background */
  --pb-surface:          #0E1420;      /* Card/surface background */
  --pb-surface-elevated: #131B2E;      /* Hover, emphasis */
  
  /* Borders & Accents */
  --pb-border:           #1E2D45;      /* Dividers, borders */
  --pb-accent:           #3B82F6;      /* Primary action, info */
  --pb-accent-hover:     #60A5FA;      /* Hover state for primary */
  
  /* Semantic Colors */
  --pb-success:          #10B981;      /* Positive, success */
  --pb-warning:          #F59E0B;      /* Caution, warning */
  --pb-danger:           #EF4444;      /* Error, destructive */
  
  /* Text */
  --pb-text-primary:     #F1F5F9;      /* Primary text */
  --pb-text-secondary:   #64748B;      /* Secondary text, muted */
  --pb-text-muted:       #334155;      /* Disabled, tertiary */
}
```

---

## 12. Typography Reference

```css
:root {
  --font-display: 'Syne', sans-serif;       /* Headings (h1–h6) */
  --font-body:    'DM Sans', sans-serif;    /* Body text, labels */
  --font-mono:    'IBM Plex Mono', monospace; /* Numbers, currency, code */
}
```

**Scale:**
```
h1: 32px, weight 700
h2: 24px, weight 700
h3: 20px, weight 700
h4: 18px, weight 600
Body: 14px–16px, weight 400
Label: 12px–14px, weight 500
Mono: 12px–14px, weight 400
```

---

## 13. Reference Components & Hooks

Key components and hooks to use and reference:

### Data States & Loading
- `src/components/probity/SkeletonLoader.tsx` — `Skeleton`, `StatCardSkeleton`, `TableSkeleton` components
- `src/components/probity/EmptyState.tsx` — empty state container with icon + title + CTA
- `src/components/ui/alert.tsx` — error/warning message displays
- `src/components/core/DataBoundary.tsx` — universal wrapper for all async states (loading, error, empty, success)

### Tables & Cards
- `src/components/probity/DataTable.tsx` — sortable, paginated data tables
- `src/components/probity/StatCard.tsx` — metric summary cards with trend indicators
- `src/components/probity/PortfolioCard.tsx` — portfolio overview cards
- `src/components/ui/table.tsx` — base table primitives (thead, tbody, th, td)

### Actions & Inputs
- `src/components/probity/ProbityButton.tsx` — buttons (primary, secondary, ghost, danger variants + sizes)
- `src/components/probity/InputField.tsx` — text inputs with state (error, success, disabled) + prefix/suffix
- `src/components/probity/DropdownSelect.tsx` — select menus with search and keyboard support
- `src/components/probity/Modal.tsx` — modals with header, body, footer (sizes: sm, md, lg, xl)
- `src/components/probity/AddPositionDrawer.tsx` — drawer pattern for side forms

### Feedback & Overlays
- `src/components/probity/Modal.tsx` — modals and dialogs
- `src/components/probity/Tooltip.tsx` — contextual help text with hover + focus support
- Toast notifications — implement using `showToast()` pattern with type (success, error, info, warning)

### Charts (Custom SVG-based)
- `src/components/probity/DonutChart.tsx` — allocation/composition breakdown (interactive segments)
- `src/components/probity/LineChart.tsx` — time-series trend charts (with ResizeObserver)
- `src/components/probity/DistributionHistogram.tsx` — histogram for Monte Carlo results (canvas-based)
- `src/components/probity/CorrelationHeatmap.tsx` — correlation matrix visualization
- `src/components/probity/Sparkline.tsx` — inline mini trend charts for cards

### Custom Hooks
- `src/hooks/useMutation.ts` — state management for async mutations (returns `state`, `execute`, `reset`)
- `src/hooks/useAuth.ts` — authentication context (login, logout, session management)
- `src/hooks/portfolio/*` — portfolio data hooks (`usePortfolios`, `usePortfolio`, `usePortfolioRiskDetail`)
- `src/hooks/simulation/*` — simulation hooks (`useSimulation`)

### Core Patterns
- `src/components/core/DataBoundary.tsx` — wraps all data-driven UI with loading/error/empty/success states
- `src/components/core/ProtectedRoute.tsx` — guards authenticated routes
- `src/components/core/ErrorBoundary.tsx` — catches render errors with recovery UI
- `src/types/shared/async-state.ts` — `AsyncState<T>` type for fetch operations
- `src/types/shared/action-state.ts` — `ActionState<T>` type for mutations (status-based)

---

## 14. Form Validation & Input Patterns

### 14.1 InputField States

All text inputs use the unified `InputField` component with explicit state management:

**Valid states:**
- `default` — initial state, no interaction
- `focused` — user is typing (blue border + glow)
- `error` — validation failed (red border + error icon + message)
- `success` — validation passed (green border + checkmark)
- `disabled` — field not editable (muted colors, no-cursor)

**Error message display:**
- Only show when `inputState="error"` and `errorMessage` prop provided
- Red text below the input with icon prefix
- Clear, actionable message (e.g., "This field is required")

**Code pattern:**
```tsx
const [email, setEmail] = useState('');
const [emailError, setEmailError] = useState('');

const handleEmailChange = (e) => {
  setEmail(e.target.value);
  setEmailError(''); // Clear error on change
};

const validateEmail = () => {
  if (!email) {
    setEmailError('Email is required');
    return false;
  }
  if (!email.includes('@')) {
    setEmailError('Enter a valid email');
    return false;
  }
  return true;
};

<InputField
  label="Email"
  value={email}
  onChange={handleEmailChange}
  errorMessage={emailError}
  inputState={emailError ? 'error' : 'default'}
  disabled={isLoading}
/>
```

### 14.2 Optional Fields

Fields can have optional suffix/prefix via the `prefix` and `suffix` props:
- Prefix: left side (e.g., "$" for currency)
- Suffix: right side (e.g., "USD" for unit)
- Both use monospace font and muted text color

**Code pattern:**
```tsx
<InputField
  label="Portfolio Value"
  type="number"
  prefix="$"
  suffix="USD"
  mono={true}
  placeholder="0.00"
/>
```

### 14.3 Form Submission Pattern

Use the `useMutation` hook for form actions. It tracks submission state automatically:

**Code pattern:**
```tsx
const { state, execute } = useMutation(async (formData) => {
  return await portfolioService.createPortfolio(formData);
});

const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();

  // Validate form first
  if (!formData.name.trim()) {
    setError('Name is required');
    return;
  }

  try {
    await execute(formData);
    // Success — state.status is now 'success'
  } catch (err) {
    // Error already in state.error
  }
};

return (
  <ProbityButton
    type="submit"
    loading={state.isPending}
    disabled={state.isPending}
  >
    {state.isPending ? 'Creating...' : 'Create'}
  </ProbityButton>
);
```

---

## 15. Animation & Timing Conventions

### 15.1 Keyframe Definitions

All animations use CSS keyframes defined inline. Common animations:

**Skeleton shimmer** (800–1200ms):
```css
@keyframes pb-skeleton {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```
Used in `Skeleton` component for all loading states.

**Modal overlay fade** (150ms):
```css
@keyframes pb-modal-overlay {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

**Modal content slide-in** (180ms):
```css
@keyframes pb-modal-in {
  from { opacity: 0; transform: translateY(8px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
```

**Button loading spinner** (700ms):
```css
@keyframes pb-spin {
  to { transform: rotate(360deg); }
}
```
Rotating border animation with 1.5px border-top transparent for the "loading" effect.

**Grid background scroll** (20s infinite):
```css
@keyframes slideGrid {
  0% { background-position: 0 0; }
  100% { background-position: 60px 60px; }
}
```
Subtle diagonal grid movement on auth pages.

**Floating particles** (8s ease-in-out infinite):
```css
@keyframes float {
  0%, 100% { transform: translateY(0) translateX(0); opacity: 0; }
  50% { opacity: 0.6; }
}
```
Gentle upward float with opacity fade.

### 15.2 Transition Timing

Micro-interactions use consistent transitions:

| Interaction | Duration | Easing |
|-------------|----------|--------|
| Hover state (button, row) | 150ms | ease-in-out |
| Focus border/shadow | 150ms | ease-in-out |
| Border color change | 150ms | ease-in-out |
| Background color shift | 150ms | ease-in-out |
| Opacity change (chart on hover) | 100–150ms | ease |
| Modal/Drawer enter | 180–200ms | ease-out |
| Modal overlay fade | 150ms | ease-out |
| Button click feedback | instant | — |

**Code pattern:**
```css
.element {
  transition: all 0.15s ease-in-out;
}

.element:hover {
  background-color: var(--pb-surface-elevated);
  box-shadow: 0 8px 16px rgba(59,130,246,0.3);
}
```

### 15.3 Loading States

Loading spinners always use the rotating border pattern (not fill):

```tsx
<span style={{
  width: "14px",
  height: "14px",
  border: "1.5px solid currentColor",
  borderTopColor: "transparent",
  borderRadius: "50%",
  animation: "pb-spin 0.7s linear infinite",
}}/>
```

Never use:
- ❌ Rotating SVG circles
- ❌ Bouncing dots
- ❌ Fill-based animations
- ❌ GIFs or images

---

## 16. Chart Implementation Patterns

### 16.1 Custom SVG Charts

The Probity platform uses custom SVG-based charts for fine-grained control and performance. All charts are implemented without Chart.js or external charting libraries.

**Common chart types:**

| Chart | Component | Use Case |
|-------|-----------|----------|
| Line chart | `LineChart.tsx` | Time-series data (portfolio returns, historical values) |
| Donut chart | `DonutChart.tsx` | Composition/breakdown (asset allocation) |
| Histogram | `DistributionHistogram.tsx` | Monte Carlo distribution results |
| Heatmap | `CorrelationHeatmap.tsx` | Asset correlation matrix |
| Sparkline | `Sparkline.tsx` | Inline mini trend (card summaries) |
| Fan chart | `FanChart.tsx` | Confidence interval bands (forecast scenarios) |

### 16.2 Responsive Chart Sizing

All charts use `ResizeObserver` to respond to container resizing:

**Code pattern:**
```tsx
const [dimensions, setDimensions] = useState({ width: 600, height: 240 });

useEffect(() => {
  if (!containerRef.current) return;

  const observer = new ResizeObserver((entries) => {
    if (entries[0]) {
      const { width, height } = entries[0].contentRect;
      if (width > 0 && height > 0) {
        setDimensions({ width, height });
      }
    }
  });

  observer.observe(containerRef.current);
  return () => observer.disconnect();
}, []);
```

Charts use SVG `viewBox` with `preserveAspectRatio="none"` to scale perfectly without distortion.

### 16.3 Chart Interaction Patterns

**Hover interactions:**
- Subtle opacity changes (50–100% opacity for non-hovered elements)
- Smooth transitions (100–150ms)
- Never block interaction while hovering
- Cursor changes to `crosshair` for time-series, `pointer` for segmented charts

**Click/Selection:**
- Donut charts allow segment selection (toggle behavior)
- Legend items are clickable alongside chart segments
- Visual feedback: opacity reduction for non-selected items
- "Click to clear" label on selected state

**Tooltips on charts:**
- Appear with slight delay (~150ms) to avoid flickering
- Show numeric value and label (e.g., "Day 42: 12.45%")
- Position intelligently (avoid screen edges)
- Use monospace font for values

**Code pattern (hover):**
```tsx
const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

<path
  opacity={hoveredIdx === null || hoveredIdx === idx ? 1 : 0.25}
  style={{ transition: 'opacity 0.1s ease' }}
  onMouseEnter={() => setHoveredIdx(idx)}
  onMouseLeave={() => setHoveredIdx(null)}
/>
```

### 16.4 Number Formatting in Charts

All numeric values in charts use monospace font (`var(--font-mono)`):

```tsx
<text
  x={cx}
  y={cy}
  fontFamily="var(--font-mono)"
  fill={color}
>
  {value.toFixed(2)}%
</text>
```

**Grid labels:** Always include 4–5 grid lines with values at consistent intervals.

---

## 17. Modal & Dialog Patterns

### 17.1 Modal Structure

Modals always follow this component hierarchy:

```tsx
<Modal
  open={isOpen}
  onClose={handleClose}
  title="Action Title"
  subtitle="Brief description"
  size="md"  // sm | md | lg | xl
  footer={<footer-buttons />}
>
  {/* form or content here */}
</Modal>
```

**Size widths:**
- `sm`: 380px (simple dialogs, confirmations)
- `md`: 520px (most forms, default)
- `lg`: 680px (complex forms, tables)
- `xl`: 860px (full-width forms, lists)

### 17.2 Focus & Keyboard Management

**Required behaviors:**
- Escape key closes modal (automatically handled by `Modal` component)
- Overlay click (outside modal) closes modal (click-outside behavior)
- First interactive element receives focus on open (manual: set `autoFocus` on first input)
- Focus trap: cannot Tab outside modal (not implemented in component — add if needed)

**Code pattern:**
```tsx
<InputField
  label="Name"
  autoFocus={true}  // Gets focus when modal opens
/>
```

### 17.3 Modal Animations

Modals use two stacked animations:
1. **Overlay fade:** 150ms opacity transition
2. **Content slide:** 180ms scale + translate

Both use `ease-out` easing. Total perceived animation: ~180ms.

**Never use:** Slide from edge, rotate, or complex transforms.

---

## 18. Button Implementation Details

### 18.1 Button Variants

The `ProbityButton` component supports 4 variants with distinct styles:

**Primary** — main actions (Save, Create, Submit):
- Base: `#3B82F6` blue
- Hover: `#60A5FA` lighter blue + shadow
- Active: `#2563EB` darker blue
- Use for: main CTAs in modals, primary actions

**Secondary** — alternative actions:
- Base: `var(--pb-surface-elevated)` dark
- Hover: slightly lighter with accent border tint
- Active: darker still
- Use for: secondary form actions, back/cancel

**Ghost** — minimal, text-like:
- Base: transparent
- Hover: subtle dark background + border
- Active: darker background
- Use for: toggles, subtle navigation, less important actions

**Danger** — destructive actions (Delete, Remove):
- Base: `rgba(239,68,68,0.1)` red tint
- Hover: stronger red with shadow
- Active: darker red
- Use for: delete confirmations, irreversible actions only

### 18.2 Button Sizes

Three fixed sizes with consistent spacing:

| Size | Padding | Font | Gap |
|------|---------|------|-----|
| `sm` | 6–12px | 12px | 6px |
| `md` | 8–16px | 13px | 7px |
| `lg` | 10–20px | 14px | 8px |

### 18.3 Button States

**Disabled:** Opacity 45%, cursor: not-allowed, no hover effects.

**Loading:** Shows rotating spinner (see spinner pattern above), disables interaction, keeps text in place.

```tsx
<ProbityButton
  loading={isSubmitting}
  disabled={isSubmitting}
  onClick={handleSubmit}
>
  {isSubmitting ? 'Saving...' : 'Save'}
</ProbityButton>
```

---

## 19. CSS Modules & Styling Strategy

### 19.1 CSS Modules Usage

The codebase uses CSS Modules for scoped styling. All page and complex component styles live in `.module.css` files:

- `auth.module.css` — auth pages (Login, Register)
- `dashboard.module.css` — dashboard layout
- `add-position-drawer.module.css` — drawer styles
- etc.

**Naming convention:** camelCase for class names, descriptive prefixes:

```css
.authContainer { }
.animatedBg { }
.gridLines { }
.floatingParticles { }
.particle { }
.card { }
.wordmark { }
.tagline { }
.form { }
.inputGroup { }
.label { }
.input { }
.errorMessage { }
.successMessage { }
```

### 19.2 Design Tokens in CSS

All color and sizing values reference CSS variables defined in `src/assets/theme.css`:

**Never hardcode:** Colors, spacing, fonts, breakpoints.

```tsx
// ❌ Don't do this
style={{ backgroundColor: '#3B82F6', padding: '16px' }}

// ✅ Do this
style={{
  backgroundColor: 'var(--pb-accent)',
  padding: '16px',
}}
```

### 19.3 Responsive Breakpoints

Media queries use pixel breakpoints (no predefined sizes, adjust as needed):

```css
@media (max-width: 480px) {
  .card { padding: 24px; }
  .input { font-size: 14px; }
}

@media (max-width: 768px) {
  .container { flex-direction: column; }
}
```

---

## 20. Figma Reference

This design system is based on the Probity Fintech Design System Figma:
[Fintech Design System](https://www.figma.com/design/hHFsgg0w6U3DahPG2qTKHE/Fintech-Design-System)

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.1 | 2026 | Added form validation patterns (InputField states, error handling), animation conventions (keyframes, timing, spinner pattern), chart implementation patterns (SVG-based, ResizeObserver, interactions), modal & dialog patterns (structure, focus, animations), button implementation details (variants, sizes, states), CSS Modules & styling strategy |
| 1.0 | 2026 | Initial global behavior rules; codified data states, semantic structure, loading strategy, micro-interactions, feedback, data formatting, edge cases, consistency rules |

---

## Questions & Updates

For questions, refer to the implementation components or contact the author.

When adding new features, update this document with any new patterns, edge cases, or exceptions that arise.
