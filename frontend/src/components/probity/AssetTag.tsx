import React from "react";

export type AssetType = "STOCK" | "ETF" | "CRYPTO" | "BOND" | "FOREX" | "COMMODITY";

interface AssetTagProps {
  type: AssetType;
  size?: "sm" | "md";
}

const CONFIG: Record<AssetType, { bg: string; border: string; text: string }> = {
  STOCK: {
    bg: "rgba(59,130,246,0.1)",
    border: "rgba(59,130,246,0.25)",
    text: "#60A5FA",
  },
  ETF: {
    bg: "rgba(139,92,246,0.1)",
    border: "rgba(139,92,246,0.25)",
    text: "#A78BFA",
  },
  CRYPTO: {
    bg: "rgba(245,158,11,0.1)",
    border: "rgba(245,158,11,0.25)",
    text: "#FCD34D",
  },
  BOND: {
    bg: "rgba(16,185,129,0.1)",
    border: "rgba(16,185,129,0.25)",
    text: "#34D399",
  },
  FOREX: {
    bg: "rgba(236,72,153,0.1)",
    border: "rgba(236,72,153,0.25)",
    text: "#F472B6",
  },
  COMMODITY: {
    bg: "rgba(249,115,22,0.1)",
    border: "rgba(249,115,22,0.25)",
    text: "#FB923C",
  },
};

export function AssetTag({ type, size = "md" }: AssetTagProps) {
  const cfg = CONFIG[type];
  const isSmall = size === "sm";

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        paddingInline: isSmall ? "6px" : "8px",
        paddingBlock: isSmall ? "1px" : "3px",
        borderRadius: "4px",
        backgroundColor: cfg.bg,
        border: `1px solid ${cfg.border}`,
        fontFamily: "var(--font-mono)",
        fontSize: isSmall ? "9px" : "10px",
        fontWeight: 500,
        letterSpacing: "0.1em",
        color: cfg.text,
        whiteSpace: "nowrap",
        userSelect: "none",
      }}
    >
      {type}
    </span>
  );
}
