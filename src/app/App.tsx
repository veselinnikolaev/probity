import React, { useState } from "react";
import { StatCard } from "./components/probity/StatCard";
import { RiskBadge } from "./components/probity/RiskBadge";
import { AssetTag } from "./components/probity/AssetTag";
import { ProbityButton } from "./components/probity/ProbityButton";
import { InputField } from "./components/probity/InputField";
import { SidebarNavItem } from "./components/probity/SidebarNavItem";
import { Tooltip } from "./components/probity/Tooltip";
import { StatCardSkeleton, TableSkeleton, Skeleton } from "./components/probity/SkeletonLoader";
import { EmptyState } from "./components/probity/EmptyState";
import { Modal } from "./components/probity/Modal";
import { DropdownSelect } from "./components/probity/DropdownSelect";
import { DataTable, DataRow } from "./components/probity/DataTable";
import { Sparkline } from "./components/probity/Sparkline";

// ─── Mock Data ───────────────────────────────────────────────────────────────

const SPARKLINE_UP   = [12,15,11,18,14,20,17,24,22,28,25,31];
const SPARKLINE_DOWN = [31,28,30,22,26,19,23,17,20,14,16,11];
const SPARKLINE_FLAT = [18,17,19,18,20,17,19,18,17,19,18,19];
const SPARKLINE_VOL  = [14,22,11,28,9,31,12,25,7,28,18,24];

const TABLE_ROWS: DataRow[] = [
  { id:"1", ticker:"NVDA",  name:"NVIDIA Corporation",       assetType:"STOCK",  price:"$875.40", change:"+12.34",  changePercent:"1.43%",  riskScore: 62, riskLevel:"MODERATE", volume:"48.2M",  marketCap:"$2.15T" },
  { id:"2", ticker:"BTC",   name:"Bitcoin",                  assetType:"CRYPTO", price:"$67,842", change:"-1,240",  changePercent:"1.79%",  riskScore: 88, riskLevel:"HIGH",     volume:"$32.4B", marketCap:"$1.33T" },
  { id:"3", ticker:"SPY",   name:"SPDR S&P 500 ETF Trust",   assetType:"ETF",    price:"$521.80", change:"+3.22",   changePercent:"0.62%",  riskScore: 22, riskLevel:"LOW",      volume:"71.5M",  marketCap:"$487B"  },
  { id:"4", ticker:"TSLA",  name:"Tesla, Inc.",               assetType:"STOCK",  price:"$178.20", change:"-8.90",   changePercent:"4.76%",  riskScore: 81, riskLevel:"HIGH",     volume:"112.4M", marketCap:"$568B"  },
  { id:"5", ticker:"ETH",   name:"Ethereum",                  assetType:"CRYPTO", price:"$3,421",  change:"+89.40",  changePercent:"2.68%",  riskScore: 74, riskLevel:"HIGH",     volume:"$18.7B", marketCap:"$411B"  },
  { id:"6", ticker:"AAPL",  name:"Apple Inc.",                assetType:"STOCK",  price:"$221.55", change:"+1.10",   changePercent:"0.50%",  riskScore: 18, riskLevel:"LOW",      volume:"53.1M",  marketCap:"$3.42T" },
  { id:"7", ticker:"QQQ",   name:"Invesco QQQ Trust",         assetType:"ETF",    price:"$437.60", change:"+4.80",   changePercent:"1.11%",  riskScore: 31, riskLevel:"LOW",      volume:"34.8M",  marketCap:"$219B"  },
  { id:"8", ticker:"META",  name:"Meta Platforms, Inc.",      assetType:"STOCK",  price:"$492.30", change:"-9.70",   changePercent:"1.93%",  riskScore: 55, riskLevel:"MODERATE", volume:"22.7M",  marketCap:"$1.25T" },
];

const DROPDOWN_OPTIONS = [
  { value: "30d",  label: "Last 30 Days",  description: "Rolling 30-day window" },
  { value: "90d",  label: "Last 90 Days",  description: "Rolling 90-day window" },
  { value: "180d", label: "Last 180 Days", description: "Semi-annual period" },
  { value: "1y",   label: "Last 12 Months",description: "Full year trailing" },
  { value: "ytd",  label: "Year to Date",  description: "Jan 1 – today" },
  { value: "all",  label: "All Time",      description: "Full data history" },
];

const NAV_SECTIONS = [
  {
    label: "FOUNDATION",
    items: [
      { id: "colors",     label: "Color Tokens",   icon: "◈" },
      { id: "typography", label: "Typography",      icon: "T" },
      { id: "spacing",    label: "Spacing & Grid",  icon: "⊞" },
    ],
  },
  {
    label: "DATA",
    items: [
      { id: "stat-cards",  label: "Stat Cards",     icon: "▣" },
      { id: "sparklines",  label: "Sparklines",     icon: "∿" },
      { id: "data-table",  label: "Data Table",     icon: "≡" },
    ],
  },
  {
    label: "INDICATORS",
    items: [
      { id: "risk-badges", label: "Risk Badges",    icon: "◉" },
      { id: "asset-tags",  label: "Asset Tags",     icon: "◆" },
    ],
  },
  {
    label: "CONTROLS",
    items: [
      { id: "buttons",     label: "Buttons",        icon: "▷" },
      { id: "inputs",      label: "Input Fields",   icon: "▭" },
      { id: "dropdowns",   label: "Dropdowns",      icon: "⌄" },
    ],
  },
  {
    label: "LAYOUT",
    items: [
      { id: "sidebar-nav", label: "Sidebar Nav",    icon: "⊣" },
      { id: "modal",       label: "Modal / Dialog", icon: "⊡" },
      { id: "tooltip",     label: "Tooltip",        icon: "◁" },
    ],
  },
  {
    label: "STATES",
    items: [
      { id: "skeleton",    label: "Skeleton Loader",icon: "□" },
      { id: "empty-state", label: "Empty State",    icon: "○" },
    ],
  },
];

// ─── Section wrapper ─────────────────────────────────────────────────────────

function Section({ id, title, subtitle, children }: { id: string; title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section id={id} style={{ marginBottom: "64px" }}>
      <div style={{ marginBottom: "24px", paddingBottom: "16px", borderBottom: "1px solid var(--pb-border)" }}>
        <h2 style={{ margin: 0, fontSize: "18px", fontFamily: "var(--font-display)", fontWeight: 700, color: "var(--pb-text-primary)" }}>
          {title}
        </h2>
        {subtitle && (
          <p style={{ margin: "6px 0 0", fontSize: "13px", fontFamily: "var(--font-body)", color: "var(--pb-text-secondary)", lineHeight: 1.5 }}>
            {subtitle}
          </p>
        )}
      </div>
      {children}
    </section>
  );
}

function ComponentRow({ label, children, vertical }: { label?: string; children: React.ReactNode; vertical?: boolean }) {
  return (
    <div style={{ marginBottom: "24px" }}>
      {label && (
        <p style={{ margin: "0 0 12px", fontSize: "11px", fontFamily: "var(--font-mono)", color: "var(--pb-text-secondary)", letterSpacing: "0.08em", textTransform: "uppercase" }}>
          {label}
        </p>
      )}
      <div style={{ display: "flex", flexWrap: "wrap", gap: "12px", alignItems: vertical ? "flex-start" : "center", flexDirection: vertical ? "column" : "row" }}>
        {children}
      </div>
    </div>
  );
}

function ColorSwatch({ color, name, hex }: { color: string; name: string; hex: string }) {
  const [copied, setCopied] = useState(false);
  const copy = () => { navigator.clipboard.writeText(hex); setCopied(true); setTimeout(() => setCopied(false), 1200); };
  return (
    <div
      onClick={copy}
      style={{ display: "flex", flexDirection: "column", gap: "8px", cursor: "pointer" }}
      title={`Copy ${hex}`}
    >
      <div
        style={{
          width: "80px",
          height: "52px",
          borderRadius: "6px",
          backgroundColor: color,
          border: "1px solid rgba(255,255,255,0.06)",
          transition: "transform 0.1s",
        }}
        onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.transform = "scale(1.04)"; }}
        onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.transform = "scale(1)"; }}
      />
      <div>
        <p style={{ margin: 0, fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-primary)", fontWeight: 500, lineHeight: 1.3 }}>{name}</p>
        <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-secondary)" }}>
          {copied ? "Copied!" : hex}
        </p>
      </div>
    </div>
  );
}

function TypeScale({ family, weight, size, sample, token }: { family: string; weight: number; size: string; sample: string; token: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "24px", paddingBlock: "14px", borderBottom: "1px solid var(--pb-border)" }}>
      <div style={{ width: "180px", flexShrink: 0 }}>
        <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-secondary)", letterSpacing: "0.06em" }}>{token}</p>
        <p style={{ margin: "2px 0 0", fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>{size} · {weight}</p>
      </div>
      <div style={{ flex: 1 }}>
        <span style={{ fontFamily: family, fontSize: size, fontWeight: weight, color: "var(--pb-text-primary)", lineHeight: 1.3 }}>
          {sample}
        </span>
      </div>
    </div>
  );
}

// ─── App ─────────────────────────────────────────────────────────────────────

export default function App() {
  const [activeNav, setActiveNav] = useState("stat-cards");
  const [selectedRows, setSelectedRows] = useState<string[]>(["2", "4"]);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSize, setModalSize] = useState<"sm" | "md" | "lg">("md");
  const [dropdownValue, setDropdownValue] = useState("90d");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const handleRowSelect = (id: string) => {
    setSelectedRows((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const scrollTo = (id: string) => {
    setActiveNav(id);
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div
      style={{
        display: "flex",
        height: "100vh",
        backgroundColor: "var(--pb-bg)",
        color: "var(--pb-text-primary)",
        overflow: "hidden",
      }}
    >
      {/* ── Sidebar ── */}
      <aside
        style={{
          width: sidebarCollapsed ? "52px" : "220px",
          minWidth: sidebarCollapsed ? "52px" : "220px",
          height: "100vh",
          backgroundColor: "var(--pb-surface)",
          borderRight: "1px solid var(--pb-border)",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          transition: "width 0.2s ease, min-width 0.2s ease",
          flexShrink: 0,
          zIndex: 10,
        }}
      >
        {/* Logo */}
        <div
          style={{
            padding: sidebarCollapsed ? "16px 0" : "20px 16px 16px",
            borderBottom: "1px solid var(--pb-border)",
            display: "flex",
            alignItems: "center",
            justifyContent: sidebarCollapsed ? "center" : "space-between",
            flexShrink: 0,
          }}
        >
          {!sidebarCollapsed && (
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <div
                style={{
                  width: "28px",
                  height: "28px",
                  borderRadius: "6px",
                  backgroundColor: "#3B82F6",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                  boxShadow: "0 0 12px rgba(59,130,246,0.4)",
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                  <path d="M12 2L2 7l10 5 10-5-10-5z" />
                  <path d="M2 17l10 5 10-5" />
                  <path d="M2 12l10 5 10-5" />
                </svg>
              </div>
              <div>
                <p style={{ margin: 0, fontFamily: "var(--font-display)", fontSize: "14px", fontWeight: 700, color: "var(--pb-text-primary)", letterSpacing: "0.02em" }}>
                  PROBITY
                </p>
                <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "9px", color: "var(--pb-text-secondary)", letterSpacing: "0.08em" }}>
                  DESIGN SYSTEM v1.0
                </p>
              </div>
            </div>
          )}
          {sidebarCollapsed && (
            <div
              style={{
                width: "28px",
                height: "28px",
                borderRadius: "6px",
                backgroundColor: "#3B82F6",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 0 12px rgba(59,130,246,0.4)",
              }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5" />
                <path d="M2 12l10 5 10-5" />
              </svg>
            </div>
          )}
          {!sidebarCollapsed && (
            <button
              onClick={() => setSidebarCollapsed(true)}
              style={{ background: "none", border: "none", color: "var(--pb-text-muted)", cursor: "pointer", padding: "2px", display: "flex" }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M11 19l-7-7 7-7"/><path d="M19 19l-7-7 7-7" opacity="0.4"/></svg>
            </button>
          )}
        </div>

        {/* Nav */}
        <div style={{ flex: 1, overflowY: "auto", padding: sidebarCollapsed ? "8px 4px" : "12px 8px" }}>
          {NAV_SECTIONS.map((section) => (
            <div key={section.label} style={{ marginBottom: "16px" }}>
              {!sidebarCollapsed && (
                <p style={{ margin: "0 0 4px 4px", fontFamily: "var(--font-mono)", fontSize: "9px", fontWeight: 500, letterSpacing: "0.12em", color: "var(--pb-text-muted)", textTransform: "uppercase" }}>
                  {section.label}
                </p>
              )}
              {section.items.map((item) => (
                sidebarCollapsed ? (
                  <Tooltip key={item.id} content={item.label} placement="right">
                    <button
                      onClick={() => scrollTo(item.id)}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        width: "36px",
                        height: "32px",
                        borderRadius: "6px",
                        border: "none",
                        margin: "2px auto",
                        backgroundColor: activeNav === item.id ? "rgba(59,130,246,0.12)" : "transparent",
                        color: activeNav === item.id ? "#60A5FA" : "var(--pb-text-secondary)",
                        cursor: "pointer",
                        fontSize: "14px",
                        ...(activeNav === item.id ? { boxShadow: "inset 2px 0 0 #3B82F6" } : {}),
                      }}
                    >
                      {item.icon}
                    </button>
                  </Tooltip>
                ) : (
                  <SidebarNavItem
                    key={item.id}
                    label={item.label}
                    icon={<span style={{ fontSize: "13px", fontFamily: "var(--font-mono)" }}>{item.icon}</span>}
                    active={activeNav === item.id}
                    onClick={() => scrollTo(item.id)}
                  />
                )
              ))}
            </div>
          ))}
        </div>

        {/* Collapse expand */}
        {sidebarCollapsed && (
          <button
            onClick={() => setSidebarCollapsed(false)}
            style={{
              background: "none",
              border: "none",
              borderTop: "1px solid var(--pb-border)",
              color: "var(--pb-text-muted)",
              cursor: "pointer",
              padding: "12px",
              display: "flex",
              justifyContent: "center",
            }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 5l7 7-7 7"/><path d="M5 5l7 7-7 7" opacity="0.4"/></svg>
          </button>
        )}

        {/* Version footer */}
        {!sidebarCollapsed && (
          <div style={{ padding: "12px 16px", borderTop: "1px solid var(--pb-border)", flexShrink: 0 }}>
            <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>
              Risk Intelligence Platform
            </p>
            <p style={{ margin: "2px 0 0", fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)", opacity: 0.6 }}>
              © 2026 Probity, Inc.
            </p>
          </div>
        )}
      </aside>

      {/* ── Main Content ── */}
      <main
        style={{
          flex: 1,
          overflowY: "auto",
          padding: "40px 48px",
          scrollBehavior: "smooth",
        }}
      >
        {/* Hero Header */}
        <div style={{ marginBottom: "56px" }}>
          <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", flexWrap: "wrap", gap: "20px" }}>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "8px" }}>
                <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "#60A5FA", backgroundColor: "rgba(59,130,246,0.1)", border: "1px solid rgba(59,130,246,0.2)", paddingInline: "8px", paddingBlock: "3px", borderRadius: "4px", letterSpacing: "0.08em" }}>
                  DESIGN SYSTEM
                </span>
                <span style={{ fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>v1.0.0 · Mar 2026</span>
              </div>
              <h1 style={{ margin: "0 0 10px", fontFamily: "var(--font-display)", fontSize: "36px", fontWeight: 800, letterSpacing: "-0.02em", color: "var(--pb-text-primary)", lineHeight: 1.1 }}>
                Probity
              </h1>
              <p style={{ margin: 0, fontFamily: "var(--font-body)", fontSize: "14px", color: "var(--pb-text-secondary)", lineHeight: 1.7, maxWidth: "560px" }}>
                Single source of truth for the Probity Risk Intelligence Platform. All tokens, components, and patterns are defined here — copy nothing, reference everything.
              </p>
            </div>
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center" }}>
              <RiskBadge level="LOW" animate />
              <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>System Health</span>
              <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "#10B981" }}>NOMINAL</span>
            </div>
          </div>

          {/* Quick stats banner */}
          <div style={{ display: "flex", gap: "1px", marginTop: "32px", borderRadius: "8px", overflow: "hidden", border: "1px solid var(--pb-border)" }}>
            {[
              { label: "Components",  value: "12" },
              { label: "Token Groups",value: "7"  },
              { label: "Typefaces",   value: "3"  },
              { label: "Color Tokens",value: "13" },
            ].map((s, i) => (
              <div
                key={i}
                style={{
                  flex: 1,
                  padding: "16px 20px",
                  backgroundColor: "var(--pb-surface)",
                  display: "flex",
                  flexDirection: "column",
                  gap: "4px",
                  borderRight: i < 3 ? "1px solid var(--pb-border)" : "none",
                }}
              >
                <span style={{ fontFamily: "var(--font-mono)", fontSize: "22px", fontWeight: 500, color: "var(--pb-text-primary)" }}>{s.value}</span>
                <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)", letterSpacing: "0.04em" }}>{s.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* ── FOUNDATION ── */}
        <Section id="colors" title="Color Tokens" subtitle="All semantic colors are defined as CSS custom properties on :root. Never use raw hex values in component code — always reference a token.">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(80px, 1fr))", gap: "20px 16px" }}>
            <ColorSwatch color="#0A0D12" name="Background"  hex="#0A0D12" />
            <ColorSwatch color="#0E1420" name="Surface"     hex="#0E1420" />
            <ColorSwatch color="#131B2E" name="Elevated"    hex="#131B2E" />
            <ColorSwatch color="#1E2D45" name="Border"      hex="#1E2D45" />
            <ColorSwatch color="#3B82F6" name="Accent"      hex="#3B82F6" />
            <ColorSwatch color="#60A5FA" name="Accent Hover"hex="#60A5FA" />
            <ColorSwatch color="#10B981" name="Success"     hex="#10B981" />
            <ColorSwatch color="#F59E0B" name="Warning"     hex="#F59E0B" />
            <ColorSwatch color="#EF4444" name="Danger"      hex="#EF4444" />
            <ColorSwatch color="#F1F5F9" name="Text Primary"hex="#F1F5F9" />
            <ColorSwatch color="#64748B" name="Text Secondary" hex="#64748B" />
            <ColorSwatch color="#334155" name="Text Muted"  hex="#334155" />
          </div>
        </Section>

        <Section id="typography" title="Typography" subtitle="Three typefaces serve distinct roles. Syne for display, DM Sans for UI copy, IBM Plex Mono for all data. Never mix roles.">
          <div style={{ backgroundColor: "var(--pb-surface)", border: "1px solid var(--pb-border)", borderRadius: "8px", padding: "4px 20px" }}>
            <TypeScale family="var(--font-display)" weight={800} size="32px" sample="Market Risk Overview — Q1 2026" token="Display / H1" />
            <TypeScale family="var(--font-display)" weight={700} size="22px" sample="Portfolio Exposure Analysis"       token="Display / H2" />
            <TypeScale family="var(--font-display)" weight={700} size="18px" sample="Sector Concentration Risk"         token="Display / H3" />
            <TypeScale family="var(--font-body)"    weight={500} size="14px" sample="Identifies correlated asset clusters across your portfolio." token="Body / Medium" />
            <TypeScale family="var(--font-body)"    weight={400} size="13px" sample="A risk score above 75 indicates elevated exposure to market volatility." token="Body / Regular" />
            <TypeScale family="var(--font-body)"    weight={400} size="11px" sample="Last updated: 15 Mar 2026, 09:41 UTC" token="Body / Caption" />
            <TypeScale family="var(--font-mono)"    weight={500} size="24px" sample="$1,284,320.00"   token="Mono / Value" />
            <TypeScale family="var(--font-mono)"    weight={400} size="13px" sample="+2.47% · 48.2M vol · P/E 28.4" token="Mono / Data" />
            <TypeScale family="var(--font-mono)"    weight={400} size="11px" sample="2026-03-15T09:41:00Z · RISK_SCORE · HIGH" token="Mono / Label" />
          </div>
        </Section>

        {/* ── STAT CARDS ── */}
        <Section id="stat-cards" title="Stat Cards" subtitle="The primary data display unit. Label uses DM Sans caps, value uses IBM Plex Mono. Always include a delta badge where change is meaningful.">
          <ComponentRow label="Live data cards">
            <StatCard label="Portfolio NAV"        value="1,284,320"  prefix="$" delta="+2.47%" deltaType="up"   sparklineData={SPARKLINE_UP}   />
            <StatCard label="Total Exposure"        value="847,210"    prefix="$" delta="-1.12%" deltaType="down" sparklineData={SPARKLINE_DOWN} riskLevel="MODERATE" />
            <StatCard label="Sharpe Ratio"          value="1.84"                  delta="+0.23"  deltaType="up"   sparklineData={SPARKLINE_FLAT} />
            <StatCard label="Beta (60-day)"         value="1.24"                  delta="+0.08"  deltaType="up"   sparklineData={SPARKLINE_VOL}  riskLevel="HIGH" />
            <StatCard label="Max Drawdown"          value="-18.4"      suffix="%"  delta="-2.1%"  deltaType="down" sparklineData={SPARKLINE_DOWN} />
            <StatCard label="VaR (95%, 1-day)"      value="24,812"     prefix="$" delta="+3.2%"  deltaType="up"  />
          </ComponentRow>
          <ComponentRow label="Loading state">
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </ComponentRow>
        </Section>

        {/* ── SPARKLINES ── */}
        <Section id="sparklines" title="Sparklines" subtitle="Inline trend visualization. Auto-colors green for positive trend, red for negative. Always sized to complement surrounding data.">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: "16px" }}>
            {[
              { label: "Uptrend",    data: SPARKLINE_UP,   w: 120, h: 40 },
              { label: "Downtrend",  data: SPARKLINE_DOWN, w: 120, h: 40 },
              { label: "Flat",       data: SPARKLINE_FLAT, w: 120, h: 40 },
              { label: "Volatile",   data: SPARKLINE_VOL,  w: 120, h: 40 },
            ].map((s) => (
              <div
                key={s.label}
                style={{ backgroundColor: "var(--pb-surface)", border: "1px solid var(--pb-border)", borderRadius: "8px", padding: "16px", display: "flex", flexDirection: "column", gap: "10px" }}
              >
                <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)", letterSpacing: "0.06em", textTransform: "uppercase" }}>{s.label}</span>
                <Sparkline data={s.data} width={s.w} height={s.h} />
              </div>
            ))}
          </div>
        </Section>

        {/* ── DATA TABLE ── */}
        <Section id="data-table" title="Data Table" subtitle="Sortable, selectable rows. Hover triggers subtle surface-elevated fill. Selected row shows accent left-border inset. All financial data uses IBM Plex Mono.">
          <DataTable
            rows={TABLE_ROWS}
            selectedIds={selectedRows}
            onSelect={handleRowSelect}
            onSelectAll={() => setSelectedRows(selectedRows.length === TABLE_ROWS.length ? [] : TABLE_ROWS.map((r) => r.id))}
            sortable
          />
          <div style={{ marginTop: "16px" }}>
            <ComponentRow label="Table skeleton">
              <div style={{ width: "100%" }}>
                <TableSkeleton rows={3} />
              </div>
            </ComponentRow>
          </div>
        </Section>

        {/* ── RISK BADGES ── */}
        <Section id="risk-badges" title="Risk Badges" subtitle="Pill-shaped, color-coded chips with dot indicators. The animated variant is reserved for live, real-time risk signals only.">
          <ComponentRow label="Standard">
            <RiskBadge level="HIGH" />
            <RiskBadge level="MODERATE" />
            <RiskBadge level="LOW" />
          </ComponentRow>
          <ComponentRow label="Animated (live signal)">
            <RiskBadge level="HIGH"     animate />
            <RiskBadge level="MODERATE" animate />
            <RiskBadge level="LOW"      animate />
          </ComponentRow>
          <ComponentRow label="Small (table / inline)">
            <RiskBadge level="HIGH"     size="sm" />
            <RiskBadge level="MODERATE" size="sm" />
            <RiskBadge level="LOW"      size="sm" />
          </ComponentRow>
        </Section>

        {/* ── ASSET TAGS ── */}
        <Section id="asset-tags" title="Asset Tags" subtitle="Compact monospace chips identifying instrument class. Use in table cells, cards, and filter chips. Each type has a dedicated color.">
          <ComponentRow label="All types">
            <AssetTag type="STOCK" />
            <AssetTag type="ETF" />
            <AssetTag type="CRYPTO" />
            <AssetTag type="BOND" />
            <AssetTag type="FOREX" />
            <AssetTag type="COMMODITY" />
          </ComponentRow>
          <ComponentRow label="Small (table cells)">
            <AssetTag type="STOCK"     size="sm" />
            <AssetTag type="ETF"       size="sm" />
            <AssetTag type="CRYPTO"    size="sm" />
            <AssetTag type="BOND"      size="sm" />
            <AssetTag type="FOREX"     size="sm" />
            <AssetTag type="COMMODITY" size="sm" />
          </ComponentRow>
        </Section>

        {/* ── BUTTONS ── */}
        <Section id="buttons" title="Buttons" subtitle="Three primary variants. Primary for confirming actions, Secondary for alternative paths, Ghost for tertiary / de-emphasized actions. Danger for irreversible actions.">
          <ComponentRow label="Sizes — Primary">
            <ProbityButton variant="primary" size="sm">Small</ProbityButton>
            <ProbityButton variant="primary" size="md">Medium</ProbityButton>
            <ProbityButton variant="primary" size="lg">Large</ProbityButton>
          </ComponentRow>
          <ComponentRow label="Variants">
            <ProbityButton variant="primary"   size="md">Run Analysis</ProbityButton>
            <ProbityButton variant="secondary" size="md">Export CSV</ProbityButton>
            <ProbityButton variant="ghost"     size="md">Dismiss</ProbityButton>
            <ProbityButton variant="danger"    size="md">Close Position</ProbityButton>
          </ComponentRow>
          <ComponentRow label="With icons">
            <ProbityButton variant="primary" iconLeft={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>}>
              Run Backtest
            </ProbityButton>
            <ProbityButton variant="secondary" iconLeft={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>}>
              Export
            </ProbityButton>
            <ProbityButton variant="ghost" iconRight={<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 18 15 12 9 6"/></svg>}>
              View Details
            </ProbityButton>
          </ComponentRow>
          <ComponentRow label="States">
            <ProbityButton variant="primary"   loading>Processing...</ProbityButton>
            <ProbityButton variant="secondary" disabled>Unavailable</ProbityButton>
            <ProbityButton variant="ghost"     disabled>Restricted</ProbityButton>
          </ComponentRow>
        </Section>

        {/* ── INPUTS ── */}
        <Section id="inputs" title="Input Fields" subtitle="1px border inputs that shift to accent on focus. Error state adds danger color + message. Prefix/suffix slots for symbols, units, and icons.">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "20px" }}>
            <InputField label="Default"  placeholder="Search tickers, names..." />
            <InputField label="Focused"  placeholder="AAPL" inputState="focused" />
            <InputField label="Error"    placeholder="0.00" inputState="error" errorMessage="Value exceeds position limit of $500,000" value="750000" readOnly />
            <InputField label="Success"  placeholder="Risk threshold" inputState="success" value="75" readOnly />
            <InputField label="Disabled" placeholder="No access" inputState="disabled" />
            <InputField label="With prefix / mono" prefix="$" placeholder="0.00" mono helperText="Enter notional value in USD" />
            <InputField label="With suffix" suffix="%" placeholder="0.00" mono helperText="Enter allocation percentage (0–100)" />
            <InputField label="Ticker search" prefix={<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>} placeholder="Search..." />
          </div>
        </Section>

        {/* ── DROPDOWNS ── */}
        <Section id="dropdowns" title="Dropdown Select" subtitle="Custom dropdown with descriptions, checked state, and smooth open animation. Closes on outside click and Escape key.">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: "20px" }}>
            <DropdownSelect
              label="Time Period"
              options={DROPDOWN_OPTIONS}
              value={dropdownValue}
              onChange={setDropdownValue}
            />
            <DropdownSelect
              label="Asset Class"
              options={[
                { value: "all",       label: "All Classes",  description: "Equities, crypto, ETFs, bonds" },
                { value: "equity",    label: "Equities",     description: "Stocks and common shares" },
                { value: "etf",       label: "ETFs",         description: "Exchange-traded funds" },
                { value: "crypto",    label: "Crypto",       description: "Digital assets" },
                { value: "fixed",     label: "Fixed Income", description: "Bonds and treasuries" },
              ]}
              value="all"
            />
            <DropdownSelect
              label="Disabled"
              options={DROPDOWN_OPTIONS}
              value="90d"
              disabled
            />
          </div>
        </Section>

        {/* ── SIDEBAR NAV ── */}
        <Section id="sidebar-nav" title="Sidebar Nav Items" subtitle="The active state uses a glowing left-border (#3B82F6) to mark the current route. Badge variants: default (blue), danger (red), warning (amber).">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: "24px" }}>
            <div style={{ backgroundColor: "var(--pb-surface)", border: "1px solid var(--pb-border)", borderRadius: "8px", padding: "8px" }}>
              <p style={{ margin: "0 0 8px 4px", fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)", letterSpacing: "0.08em" }}>STATES</p>
              <SidebarNavItem label="Default item" icon={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>} />
              <SidebarNavItem label="Active item (current)" icon={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>} active />
              <SidebarNavItem label="With badge count" icon={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></svg>} badge={3} />
              <SidebarNavItem label="Danger alert" icon={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>} badge={12} badgeVariant="danger" />
              <SidebarNavItem label="Warning state" icon={<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>} badge="!" badgeVariant="warning" />
            </div>
          </div>
        </Section>

        {/* ── MODAL ── */}
        <Section id="modal" title="Modal / Dialog Shell" subtitle="Backdrop blur overlay, glowing top border, smooth entrance animation. Closes on Escape or clicking outside.">
          <ComponentRow label="Launch modal">
            <ProbityButton variant="primary" onClick={() => { setModalSize("sm"); setModalOpen(true); }}>Small Modal</ProbityButton>
            <ProbityButton variant="secondary" onClick={() => { setModalSize("md"); setModalOpen(true); }}>Medium Modal</ProbityButton>
            <ProbityButton variant="ghost" onClick={() => { setModalSize("lg"); setModalOpen(true); }}>Large Modal</ProbityButton>
          </ComponentRow>

          {/* Modal specimen (static display) */}
          <div style={{ position: "relative", height: "260px", backgroundColor: "rgba(10,13,18,0.6)", border: "1px solid var(--pb-border)", borderRadius: "8px", overflow: "hidden", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <div style={{ backgroundColor: "var(--pb-surface-elevated)", border: "1px solid var(--pb-border)", borderRadius: "10px", width: "460px", maxWidth: "90%", overflow: "hidden", boxShadow: "0 24px 64px rgba(0,0,0,0.6)", position: "relative" }}>
              <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: "1px", background: "linear-gradient(90deg, transparent 0%, rgba(59,130,246,0.5) 50%, transparent 100%)" }} />
              <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--pb-border)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <p style={{ margin: 0, fontFamily: "var(--font-display)", fontSize: "14px", fontWeight: 700, color: "var(--pb-text-primary)" }}>Confirm Position Close</p>
                  <p style={{ margin: "3px 0 0", fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>This action cannot be undone.</p>
                </div>
                <div style={{ width: "24px", height: "24px", borderRadius: "5px", border: "1px solid var(--pb-border)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--pb-text-secondary)", fontSize: "14px" }}>×</div>
              </div>
              <div style={{ padding: "16px 20px" }}>
                <p style={{ margin: 0, fontFamily: "var(--font-body)", fontSize: "12px", color: "var(--pb-text-secondary)", lineHeight: 1.6 }}>
                  You are about to close your entire <span style={{ fontFamily: "var(--font-mono)", color: "var(--pb-text-primary)" }}>NVDA</span> position valued at <span style={{ fontFamily: "var(--font-mono)", color: "#10B981" }}>$875,400.00</span>. Market conditions apply.
                </p>
              </div>
              <div style={{ padding: "12px 20px", borderTop: "1px solid var(--pb-border)", display: "flex", justifyContent: "flex-end", gap: "8px" }}>
                <ProbityButton variant="ghost" size="sm">Cancel</ProbityButton>
                <ProbityButton variant="danger" size="sm">Close Position</ProbityButton>
              </div>
            </div>
            <div style={{ position: "absolute", bottom: "12px", right: "16px", fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>specimen preview</div>
          </div>
        </Section>

        {/* ── TOOLTIP ── */}
        <Section id="tooltip" title="Tooltip" subtitle="Dark surface tooltip that appears on hover. Use for data labels, truncated text, and contextual metadata. Never for primary content.">
          <ComponentRow label="Placements">
            <Tooltip content="Portfolio volatility over the trailing 60 days" placement="top">
              <ProbityButton variant="secondary" size="sm">Hover Top</ProbityButton>
            </Tooltip>
            <Tooltip content="Sharpe ratio above 1.5 is considered strong risk-adjusted performance." placement="bottom">
              <ProbityButton variant="secondary" size="sm">Hover Bottom</ProbityButton>
            </Tooltip>
            <Tooltip content="Sector exposure limit exceeded" placement="right">
              <ProbityButton variant="secondary" size="sm">Hover Right</ProbityButton>
            </Tooltip>
          </ComponentRow>
          <ComponentRow label="Rich tooltip (data context)">
            <Tooltip
              content={
                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "16px" }}>
                    <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>Risk Score</span>
                    <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "#EF4444" }}>88 / 100</span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "16px" }}>
                    <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>Beta</span>
                    <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "var(--pb-text-primary)" }}>1.74</span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "16px" }}>
                    <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>30d Vol</span>
                    <span style={{ fontFamily: "var(--font-mono)", fontSize: "11px", color: "#F59E0B" }}>62.4%</span>
                  </div>
                </div>
              }
              placement="top"
            >
              <span
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  fontFamily: "var(--font-mono)",
                  fontSize: "13px",
                  color: "var(--pb-text-primary)",
                  backgroundColor: "var(--pb-surface)",
                  border: "1px solid var(--pb-border)",
                  borderRadius: "6px",
                  padding: "8px 12px",
                  cursor: "default",
                }}
              >
                BTC — Bitcoin
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--pb-text-secondary)" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
              </span>
            </Tooltip>
          </ComponentRow>
        </Section>

        {/* ── SKELETON ── */}
        <Section id="skeleton" title="Skeleton Loader" subtitle="Used for async content states. Shimmer animation signals loading without layout shift. Match skeleton dimensions to actual content.">
          <ComponentRow label="Card skeletons">
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </ComponentRow>
          <ComponentRow label="Inline skeletons" vertical>
            <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
              <Skeleton width={36} height={36} borderRadius={6} />
              <div style={{ display: "flex", flexDirection: "column", gap: "6px", flex: 1 }}>
                <Skeleton width="50%" height={12} />
                <Skeleton width="30%" height={10} />
              </div>
              <Skeleton width={60} height={12} />
            </div>
            <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
              <Skeleton width={36} height={36} borderRadius={6} />
              <div style={{ display: "flex", flexDirection: "column", gap: "6px", flex: 1 }}>
                <Skeleton width="60%" height={12} />
                <Skeleton width="35%" height={10} />
              </div>
              <Skeleton width={60} height={12} />
            </div>
          </ComponentRow>
          <ComponentRow label="Table skeleton">
            <div style={{ width: "100%" }}>
              <TableSkeleton rows={4} />
            </div>
          </ComponentRow>
        </Section>

        {/* ── EMPTY STATE ── */}
        <Section id="empty-state" title="Empty State" subtitle="Dashed border container with instructional copy. Used when a data view has no results. Always include a primary action to resolve the empty state.">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "16px" }}>
            <EmptyState
              title="No Positions Found"
              description="Your portfolio has no open positions matching the current filters. Adjust your filters or add a new position to begin analysis."
              icon={
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--pb-text-muted)" strokeWidth="1.2">
                  <rect x="2" y="3" width="20" height="14" rx="2"/>
                  <line x1="8" y1="21" x2="16" y2="21"/>
                  <line x1="12" y1="17" x2="12" y2="21"/>
                </svg>
              }
              action={<ProbityButton variant="primary" size="sm">Add Position</ProbityButton>}
            />
            <EmptyState
              title="No Risk Alerts"
              description="All positions are within defined risk thresholds. No action required at this time."
              icon={
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--pb-text-muted)" strokeWidth="1.2">
                  <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
              }
            />
            <EmptyState
              title="No Data Available"
              description="Historical data for this instrument has not yet been ingested. Check back after the next data sync."
              size="sm"
            />
          </div>
        </Section>

        {/* Footer */}
        <div style={{ marginTop: "40px", paddingTop: "24px", borderTop: "1px solid var(--pb-border)", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div style={{ width: "20px", height: "20px", borderRadius: "4px", backgroundColor: "#3B82F6", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
            </div>
            <span style={{ fontFamily: "var(--font-display)", fontSize: "12px", fontWeight: 700, color: "var(--pb-text-primary)" }}>PROBITY</span>
            <span style={{ fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>Design System v1.0.0</span>
          </div>
          <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "10px", color: "var(--pb-text-muted)" }}>
            Internal use only · Risk Intelligence Platform · © 2026 Probity, Inc.
          </p>
        </div>
      </main>

      {/* ── Live Modal ── */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Confirm Position Close"
        subtitle="This action is irreversible once market order is submitted."
        size={modalSize}
        footer={
          <>
            <ProbityButton variant="ghost" size="sm" onClick={() => setModalOpen(false)}>Cancel</ProbityButton>
            <ProbityButton variant="danger" size="sm" onClick={() => setModalOpen(false)}>
              Close Position
            </ProbityButton>
          </>
        }
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          <p style={{ margin: 0, fontFamily: "var(--font-body)", fontSize: "13px", color: "var(--pb-text-secondary)", lineHeight: 1.7 }}>
            You are about to submit a market order to close your entire{" "}
            <span style={{ fontFamily: "var(--font-mono)", color: "var(--pb-text-primary)" }}>NVDA</span>{" "}
            position. The estimated proceeds are{" "}
            <span style={{ fontFamily: "var(--font-mono)", color: "#10B981" }}>$875,400.00</span>{" "}
            based on last traded price. Final execution price may differ.
          </p>
          <div style={{ backgroundColor: "rgba(239,68,68,0.05)", border: "1px solid rgba(239,68,68,0.15)", borderRadius: "6px", padding: "12px 14px", display: "flex", gap: "10px", alignItems: "flex-start" }}>
            <span style={{ color: "#EF4444", flexShrink: 0, marginTop: "1px" }}>⚠</span>
            <p style={{ margin: 0, fontFamily: "var(--font-body)", fontSize: "12px", color: "#EF4444", lineHeight: 1.6 }}>
              This position has an active stop-loss at $820.00. Closing manually will override your risk management settings.
            </p>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
            {[
              { label: "Position Size",   value: "1,000 shares" },
              { label: "Current Price",   value: "$875.40" },
              { label: "Est. Proceeds",   value: "$875,400.00" },
              { label: "P&L",             value: "+$124,600.00" },
            ].map((d) => (
              <div key={d.label} style={{ backgroundColor: "var(--pb-surface)", border: "1px solid var(--pb-border)", borderRadius: "6px", padding: "10px 12px" }}>
                <p style={{ margin: "0 0 3px", fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>{d.label}</p>
                <p style={{ margin: 0, fontFamily: "var(--font-mono)", fontSize: "13px", color: "var(--pb-text-primary)" }}>{d.value}</p>
              </div>
            ))}
          </div>
        </div>
      </Modal>
    </div>
  );
}
