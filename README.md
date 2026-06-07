# Probity — Portfolio Risk Analytics Platform

**Probity** is a full-stack portfolio risk analytics platform designed to help investors understand and manage portfolio risk through advanced modeling and visualization. This repository contains the React/TypeScript frontend.

Built solo as a full-stack project combining a Spring Boot backend with a custom-designed React frontend, component library, and design system.

## Platform Features

### Dashboard
Real-time portfolio overview with key metrics, asset allocation breakdown, and risk indicators. Combines live data with interactive summaries for at-a-glance insights.

### Monte Carlo Simulation
Advanced portfolio risk modeling with configurable simulation parameters. Visualizes probability distributions and stress-test scenarios to help understand portfolio behavior under various market conditions.

### Authentication & Settings
Secure user authentication with session management. Settings interface for portfolio configuration, risk parameters, and user preferences.

### Data Visualization
Interactive custom charts for time-series analysis, distribution plots, and correlation matrices. Built with SVG and Canvas for fine-grained control and performance. Real-time updates with responsive design.

## Tech Stack

- **Frontend:** React 18, TypeScript, Vite
- **Styling:** Tailwind CSS with custom design tokens
- **Components:** Radix UI, shadcn/ui primitives
- **Charts:** Custom SVG and Canvas implementations
- **HTTP:** Axios with CSRF protection and request interceptors
- **Backend:** [Java 21, Spring Boot, Hexagonal Architecture][1]

## Architecture & Technical Highlights

### State Management Patterns
- **Typed Async States:** Generic `AsyncState<T>` and `ActionState<T>` types enforce consistency across all data fetches and mutations (loading, success, error states)
- **Data Boundary Pattern:** Universal `DataBoundary<T>` component guarantees uniform handling of loading, error, empty, and success states across the application — eliminates inconsistent loaders and error handling
- **Custom Hooks:** Specialized hooks (`usePortfolio`, `usePortfolios`, `useSimulation`, etc.) encapsulate data-fetching logic with automatic state management

### Security
- **CSRF Protection:** Axios interceptors automatically attach and rotate CSRF tokens on every request
- **Session Restore:** Auth context performs silent token refresh on mount — seamlessly restores sessions after page reloads
- **Protected Routes:** React Router guards ensure only authenticated users access app routes; unauthenticated requests redirect to login

### API Service Layer
- **Axios Interceptors:** Request/response interceptors handle CSRF token lifecycle and centralize error handling
- **Type-Safe Endpoints:** Service layer (`portfolioService`, `simulationService`, `authService`) maps all backend endpoints with strict TypeScript types
- **Credential Handling:** `withCredentials: true` ensures secure cookie-based session management

### Component System
The frontend is built on a **custom component library and design system** designed and implemented from scratch:

- **Design Tokens:** Centralized color palette, typography, and spacing via CSS variables (`src/assets/theme.css`)
- **Semantic Primitives:** Built on Radix UI for accessible, unstyled foundations
- **Data State Handling:** Every component handles loading, empty, error, and partial states — no missing loaders or inconsistent error messages
- **Accessibility:** WCAG-compliant components with keyboard navigation, semantic HTML, and screen reader support
- **Design File:** [Figma design system](https://www.figma.com/design/hHFsgg0w6U3DahPG2qTKHE/Fintech-Design-System)

For detailed design principles and implementation guidelines, see [Global Behavior Rules](./docs/GLOBAL_BEHAVIOR_RULES.md).

### Error Handling
- **Error Boundary:** React error boundary catches and displays render-time errors with recovery UI
- **Async Error States:** All data fetches capture and display error messages from server responses
- **Fallback UI:** Loading spinners, empty states, and error messages provide clear feedback throughout the application

## Project Structure

```
src/
├── components/
│   ├── ui/              # Radix/shadcn primitives (unstyled)
│   ├── probity/         # Product-specific components (data visualization, tables, forms)
│   ├── auth/            # Authentication UI
│   └── core/            # Core patterns (DataBoundary, ProtectedRoute, ErrorBoundary)
├── pages/               # Page-level layouts and features
├── services/            # API service layer with typed endpoints
├── hooks/               # Custom React hooks for data fetching and state
│   ├── portfolio/       # Portfolio data hooks
│   ├── simulation/      # Monte Carlo simulation hooks
│   ├── settings/        # User settings and preferences
│   └── auth/            # Authentication hooks
├── context/             # Context providers (Auth)
├── types/               # TypeScript type definitions
│   ├── shared/          # AsyncState, ActionState patterns
│   ├── portfolio/       # Portfolio types
│   ├── simulation/      # Simulation types
│   └── auth/            # Auth types
├── assets/              # Design tokens and global styles
├── constants/           # Route definitions and app constants
└── docs/                # Design system documentation
```

## Getting Started

### Prerequisites
- Node.js 18+
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend will be available at `http://localhost:5173` (or your configured dev server URL).

### Building for Production

```bash
npm run build
```

## Key Components

### Data State Management
- **SkeletonLoader** — Progressive loading with shimmer animation
- **EmptyState** — Dashed containers for empty data states
- **ErrorState** — Error message display with recovery actions
- **DataBoundary** — Universal wrapper for consistent async state handling

### Visualizations
- **DonutChart** — Portfolio composition breakdown
- **LineChart** — Time-series portfolio performance
- **CorrelationHeatmap** — Asset correlation matrix visualization
- **DistributionHistogram** — Monte Carlo result distribution
- **Sparkline** — Compact metric trends

### Forms & Inputs
- **InputField** — Accessible text inputs with labels
- **DropdownSelect** — Semantic select menus with search
- **ProbityButton** — Primary, secondary, ghost, danger variants

### Complex Components
- **DataTable** — Sortable, paginated tables with semantic HTML
- **PortfolioCard** — Portfolio summary cards with live data
- **Modal** & **Drawer** — Accessible overlays for forms and actions
- **Tooltip** — Contextual help text

## Design Patterns

### Type-Safe API Calls
All backend endpoints are wrapped in a service layer with strict TypeScript types:

```typescript
const portfolio = await portfolioService.getPortfolios();
const summary = await portfolioService.getSummary(id, dateRange);
const varReport = await portfolioService.getVaRReport(id, confidence, horizon);
```

### Unified Async State Management
Every data fetch follows the same pattern:

```typescript
const { state, execute } = useMutation(portfolioService.createPortfolio);
// state: { status, error, data }
// isPending, isSuccess, isError computed from status
```

### Error Recovery
Silent token refresh on mount restores sessions; explicit logout clears auth state. All requests retry with fresh CSRF tokens if rotation occurs.

## Full-Stack Project

This is one half of Probity. The complete platform includes:

- **Frontend (this repo):** React/TypeScript interface for portfolio analysis and Monte Carlo visualization
- **Backend:** [Java 21, Spring Boot backend with hexagonal architecture and Monte Carlo simulations][1]

[1]: https://github.com/veselinnikolaev/probity-backend

---

Built by [Veselin Nikolaev](https://www.linkedin.com/in/veselin-nikolaev/)
