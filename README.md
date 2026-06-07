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
Interactive charts built with Chart.js for time-series analysis, distribution plots, and correlation matrices. Real-time updates with responsive design.

## Tech Stack

- **Frontend:** React 18, TypeScript, Vite
- **Styling:** Tailwind CSS with custom design tokens
- **Components:** Radix UI, shadcn/ui primitives
- **Charts:** Chart.js
- **Backend:** [Java 21, Spring Boot, Hexagonal Architecture][1]

## Design & Implementation

The frontend is built on a **custom component library and design system** I designed and implemented from scratch:

- **Design Tokens:** Centralized color palette, typography, and spacing (`src/assets/theme.css`)
- **Component System:** Semantic HTML primitives with accessibility built-in
- **Data State Handling:** Loading, empty, error, and partial states across all components
- **Accessibility:** WCAG-compliant components with keyboard navigation and screen reader support
- **Design File:** [Figma design system](https://www.figma.com/design/hHFsgg0w6U3DahPG2qTKHE/Fintech-Design-System)

For detailed design principles and implementation guidelines, see [Global Behavior Rules](./src/docs/GLOBAL_BEHAVIOR_RULES.md).

## Project Structure

```
src/
├── components/
│   ├── ui/              # Base Radix/shadcn primitives
│   ├── probity/         # Product-specific components
│   └── auth/            # Authentication UI
├── pages/               # Page-level layouts
├── api/                 # API service layer
├── hooks/               # Custom React hooks
├── context/             # Context providers
├── types/               # TypeScript definitions
├── assets/              # Design tokens and global styles
└── docs/                # Design documentation
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

### Data States
- **SkeletonLoader** — Progressive loading with shimmer animation
- **EmptyState** — Dashed containers for empty data states
- **Alert Components** — Error and warning messaging

### Visualizations
- **DonutChart** — Portfolio composition breakdown
- **LineChart** — Time-series portfolio performance
- **Sparkline** — Compact metric trends

### Forms & Inputs
- **InputField** — Accessible text inputs with labels
- **DropdownSelect** — Semantic select menus
- **ProbityButton** — Primary, secondary, ghost variants

## Full-Stack Project

This is one half of Probity. The complete platform includes:

- **Frontend (this repo):** React/TypeScript interface for portfolio analysis and Monte Carlo visualization
- **Backend:** [Java 21, Spring Boot backend with hexagonal architecture and Monte Carlo simulations][1]

[1]: [link]

---

Built by [Veselin Nikolaev](https://linkedin.com/in/veselinnikolaev)
