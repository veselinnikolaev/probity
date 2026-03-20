import React from "react";

export type RiskLevel = "HIGH" | "MODERATE" | "LOW";

interface RiskBadgeProps {
  level: RiskLevel;
  size?: "sm" | "md";
  animate?: boolean;
}

const CONFIG: Record<
  RiskLevel,
  { label: string; bg: string; border: string; text: string; dot: string; glow: string }
> = {
  HIGH: {
    label: "HIGH RISK",
    bg: "rgba(239,68,68,0.08)",
    border: "rgba(239,68,68,0.3)",
    text: "#EF4444",
    dot: "#EF4444",
    glow: "rgba(239,68,68,0.15)",
  },
  MODERATE: {
    label: "MODERATE",
    bg: "rgba(245,158,11,0.08)",
    border: "rgba(245,158,11,0.3)",
    text: "#F59E0B",
    dot: "#F59E0B",
    glow: "rgba(245,158,11,0.12)",
  },
  LOW: {
    label: "LOW RISK",
    bg: "rgba(16,185,129,0.08)",
    border: "rgba(16,185,129,0.3)",
    text: "#10B981",
    dot: "#10B981",
    glow: "rgba(16,185,129,0.12)",
  },
};

export function RiskBadge({ level, size = "md", animate = false }: RiskBadgeProps) {
  const cfg = CONFIG[level];
  const isSmall = size === "sm";

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: isSmall ? "4px" : "6px",
        paddingInline: isSmall ? "8px" : "10px",
        paddingBlock: isSmall ? "2px" : "4px",
        borderRadius: "9999px",
        backgroundColor: cfg.bg,
        border: `1px solid ${cfg.border}`,
        boxShadow: `0 0 8px ${cfg.glow}`,
        fontFamily: "var(--font-mono)",
        fontSize: isSmall ? "10px" : "11px",
        fontWeight: 500,
        letterSpacing: "0.08em",
        color: cfg.text,
        whiteSpace: "nowrap",
        userSelect: "none",
      }}
    >
      <span
        style={{
          width: isSmall ? "5px" : "6px",
          height: isSmall ? "5px" : "6px",
          borderRadius: "50%",
          backgroundColor: cfg.dot,
          flexShrink: 0,
          ...(animate
            ? {
                animation: "pb-pulse 2s cubic-bezier(0.4,0,0.6,1) infinite",
                boxShadow: `0 0 4px ${cfg.dot}`,
              }
            : {}),
        }}
      />
      {cfg.label}
      <style>{`
        @keyframes pb-pulse {
          0%,100%{opacity:1}
          50%{opacity:0.4}
        }
      `}</style>
    </span>
  );
}
