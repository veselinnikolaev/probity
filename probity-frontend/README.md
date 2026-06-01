
  # Probity Design System

This is the production code bundle for the **Probity Fintech Design System**, a comprehensive design language and component library for financial platforms.

## Design Documentation

- **[Global Behavior Rules](./src/docs/GLOBAL_BEHAVIOR_RULES.md)** — Unified patterns for data states, accessibility, loading strategies, micro-interactions, and consistency rules across all screens
  - Data state handling (loading, empty, error, partial)
  - Semantic HTML structure and accessibility guidelines
  - Data formatting rules (currency, percentages, dates)
  - Edge case handling (zero values, missing data, large values)
  - Consistency patterns for reusable components

## Design System Resources

- **Figma Design File:** https://www.figma.com/design/hHFsgg0w6U3DahPG2qTKHE/Fintech-Design-System
- **Design Tokens:** `src/assets/theme.css` (colors, typography, CSS variables)
- **Component Library:** `src/components/`
  - Base UI primitives: `src/components/ui/`
  - Probity-specific components: `src/components/probity/`

## Quick Start

Run `npm i` to install the dependencies.

Run `npm run dev` to start the development server.

The application will be available at the configured dev server URL.

## Project Structure

```
src/
├── assets/              # Global styles and design tokens
│   ├── theme.css       # Color palette, typography, CSS variables
│   ├── tailwind.css    # Tailwind CSS entry point
│   └── index.css       # Global CSS imports
├── components/
│   ├── ui/             # Base Radix/shadcn-style primitives
│   ├── probity/        # Probity-specific component system
│   └── auth/           # Authentication components
├── pages/              # Page-level layouts and screens
├── api/                # API service layer
├── hooks/              # Custom React hooks
├── context/            # Context providers
├── types/              # TypeScript type definitions
└── docs/               # Design system documentation
```

## Key Design System Principles

1. **Data States First** — Every component handles loading, empty, error, and partial data states
2. **Semantic HTML** — Proper heading hierarchy, table structure, and ARIA attributes
3. **Progressive Loading** — Show layouts immediately with skeletons; replace section by section
4. **Accessible By Default** — Keyboard navigation, screen reader support, color + non-color signals
5. **Consistent Patterns** — Reuse components, maintain action placement, same state = same UI
6. **Data Formatting** — Numbers in IBM Plex Mono with proper currency, percentage, and date formats
7. **Micro-interactions** — Smooth, responsive feedback with <200ms animations

## Component Usage

Always refer to existing components when building features:

### Data States
- `SkeletonLoader` — Loading state with shimmer animation
- `EmptyState` — Dashed border container with icon, message, and CTA
- Alert components — Error and warning messages

### Tables & Cards
- `DataTable` — Semantic table with headers and proper markup
- `StatCard` — Metric summary cards
- `PortfolioCard` — Content cards for portfolio displays

### Actions
- `ProbityButton` — Primary, secondary, ghost, and danger button variants
- `InputField` — Form inputs with labels
- `DropdownSelect` — Accessible select menus
- `Modal` & `Tooltip` — Overlays and help text

### Charts
- `DonutChart`, `LineChart`, `Sparkline` — Data visualization components

For complete implementation guidance, see [Global Behavior Rules](./src/docs/GLOBAL_BEHAVIOR_RULES.md).

## Contributing to the Design System

When adding new features or components:

1. **Read the Global Behavior Rules** — Ensure your implementation follows the established patterns
2. **Reuse Components** — Don't create custom variants; extend or reuse existing components
3. **Use Design Tokens** — Always reference `--pb-*` CSS variables, never hardcode colors
4. **Handle Data States** — Every data-driven component must support all 4 states
5. **Test Accessibility** — Keyboard navigation, screen readers, color contrast
6. **Follow Semantic HTML** — Use proper heading structure, `<table>`, `<label>`, etc.
7. **Format Data Consistently** — Use IBM Plex Mono for numbers; follow currency/date rules

---

**Last Updated:** 2026
**Maintainer:** Probity Design System Team
  
