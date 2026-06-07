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

## 13. Reference Components

Key components to use and reference:

### Data States
- `src/components/probity/SkeletonLoader.tsx` — loading state
- `src/components/probity/EmptyState.tsx` — empty state
- `src/components/ui/alert.tsx` — error/warning messages

### Tables & Cards
- `src/components/probity/DataTable.tsx` — data tables
- `src/components/probity/StatCard.tsx` — metric cards
- `src/components/probity/PortfolioCard.tsx` — content cards

### Actions & Inputs
- `src/components/probity/ProbityButton.tsx` — buttons
- `src/components/probity/InputField.tsx` — form inputs
- `src/components/probity/DropdownSelect.tsx` — dropdowns

### Feedback & Overlays
- Toast notifications (implement as needed)
- `src/components/probity/Modal.tsx` — modals
- `src/components/probity/Tooltip.tsx` — tooltips

### Charts
- `src/components/probity/DonutChart.tsx` — allocation charts
- `src/components/probity/LineChart.tsx` — trend charts
- `src/components/probity/Sparkline.tsx` — inline mini charts

---

## 14. Figma Reference

This design system is based on the Probity Fintech Design System Figma:
[Fintech Design System](https://www.figma.com/design/hHFsgg0w6U3DahPG2qTKHE/Fintech-Design-System)

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026 | Initial global behavior rules; codified data states, semantic structure, loading strategy, micro-interactions, feedback, data formatting, edge cases, consistency rules |

---

## Questions & Updates

For questions, refer to the implementation components or contact the author.

When adding new features, update this document with any new patterns, edge cases, or exceptions that arise.
