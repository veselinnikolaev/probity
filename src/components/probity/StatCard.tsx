import React from "react";
import { Sparkline } from "./Sparkline";
import { RiskBadge, RiskLevel } from "./RiskBadge";

interface StatCardProps {
  label: string;
  value: string;
  subValue?: string;
  delta?: string;
  deltaType?: "up" | "down" | "neutral";
  sparklineData?: number[];
  riskLevel?: RiskLevel;
  loading?: boolean;
  suffix?: string;
  prefix?: string;
  className?: string;
}

export function StatCard({
  label,
  value,
  subValue,
  delta,
  deltaType = "neutral",
  sparklineData,
  riskLevel,
  loading = false,
  suffix,
  prefix,
  className,
}: StatCardProps) {
  if (loading) {
    return (
      <div
        style={{
          backgroundColor: "var(--pb-surface)",
          border: "1px solid var(--pb-border)",
          borderRadius: "8px",
          padding: "20px",
          display: "flex",
          flexDirection: "column",
          gap: "12px",
          minWidth: "200px",
        }}
        className={className}
      >
        <div style={{ height: "11px", width: "60%", borderRadius: "4px", background: "var(--pb-surface-elevated)", animation: "pb-shimmer 1.5s ease-in-out infinite" }} />
        <div style={{ height: "28px", width: "80%", borderRadius: "4px", background: "var(--pb-surface-elevated)", animation: "pb-shimmer 1.5s ease-in-out infinite 0.1s" }} />
        <div style={{ height: "11px", width: "40%", borderRadius: "4px", background: "var(--pb-surface-elevated)", animation: "pb-shimmer 1.5s ease-in-out infinite 0.2s" }} />
      </div>
    );
  }

  const deltaColor =
    deltaType === "up"
      ? "#10B981"
      : deltaType === "down"
      ? "#EF4444"
      : "var(--pb-text-secondary)";

  const deltaArrow = deltaType === "up" ? "▲" : deltaType === "down" ? "▼" : "—";

  return (
    <div
      style={{
        backgroundColor: "var(--pb-surface)",
        border: "1px solid var(--pb-border)",
        borderRadius: "8px",
        padding: "20px",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        minWidth: "200px",
        position: "relative",
        overflow: "hidden",
        transition: "border-color 0.15s",
      }}
      className={`stat-card ${className ?? ""}`}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLDivElement).style.borderColor = "rgba(59,130,246,0.35)";
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLDivElement).style.borderColor = "var(--pb-border)";
      }}
    >
      {/* Subtle top accent */}
      <div
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          height: "1px",
          background: "linear-gradient(90deg, transparent, rgba(59,130,246,0.4), transparent)",
          opacity: 0.6,
        }}
      />

      {/* Label row */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <span
          style={{
            fontFamily: "var(--font-body)",
            fontSize: "11px",
            fontWeight: 500,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            color: "var(--pb-text-secondary)",
          }}
        >
          {label}
        </span>
        {riskLevel && <RiskBadge level={riskLevel} size="sm" />}
      </div>

      {/* Value row */}
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "12px" }}>
        <div>
          <div
            style={{
              fontFamily: "var(--font-mono)",
              fontSize: "24px",
              fontWeight: 500,
              color: "var(--pb-text-primary)",
              lineHeight: 1.1,
              letterSpacing: "-0.02em",
            }}
          >
            {prefix && (
              <span style={{ fontSize: "14px", color: "var(--pb-text-secondary)", marginRight: "2px" }}>
                {prefix}
              </span>
            )}
            {value}
            {suffix && (
              <span style={{ fontSize: "14px", color: "var(--pb-text-secondary)", marginLeft: "2px" }}>
                {suffix}
              </span>
            )}
          </div>
          {subValue && (
            <div
              style={{
                fontFamily: "var(--font-mono)",
                fontSize: "11px",
                color: "var(--pb-text-secondary)",
                marginTop: "2px",
              }}
            >
              {subValue}
            </div>
          )}
        </div>

        {sparklineData && <Sparkline data={sparklineData} width={72} height={28} />}
      </div>

      {/* Delta */}
      {delta && (
        <div style={{ display: "flex", alignItems: "center", gap: "6px", marginTop: "2px" }}>
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "3px",
              fontFamily: "var(--font-mono)",
              fontSize: "11px",
              fontWeight: 500,
              color: deltaColor,
              backgroundColor: `${deltaColor}14`,
              paddingInline: "6px",
              paddingBlock: "2px",
              borderRadius: "4px",
            }}
          >
            <span style={{ fontSize: "8px" }}>{deltaArrow}</span>
            {delta}
          </span>
          <span style={{ fontFamily: "var(--font-body)", fontSize: "11px", color: "var(--pb-text-secondary)" }}>
            vs. prev period
          </span>
        </div>
      )}

      <style>{`
        .stat-card { cursor: default; }
        @keyframes pb-shimmer {
          0%,100%{opacity:0.5}
          50%{opacity:1}
        }
      `}</style>
    </div>
  );
}
