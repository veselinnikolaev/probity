import React from "react";
import { Sparkline } from "./Sparkline";
import { RiskBadge, RiskLevel } from "./RiskBadge";

export interface  StatCardProps {
    label: string;
    value: string;
    subValue?: string;
    delta?: string;
    deltaType?: "up" | "down" | "neutral";
    // FIX: When true, the sparkline colour is inverted — a rising line is drawn
    // in red and a falling line in green. Use this for metrics where higher is
    // *worse* (e.g. Portfolio Volatility, VaR).
    invertSentiment?: boolean;
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
                             invertSentiment = false,
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

    const deltaColor = (() => {
        if (deltaType === "neutral") return "var(--pb-text-secondary)";

        if (invertSentiment) {
            // Higher is worse (Red), Lower is better (Green)
            return deltaType === "up" ? "#EF4444" : "#10B981";
        }
        // Normal: Higher is better (Green), Lower is worse (Red)
        return deltaType === "up" ? "#10B981" : "#EF4444";
    })();

    const deltaArrow = deltaType === "up" ? "▲" : deltaType === "down" ? "▼" : "—";

    // FIX: Compute the sparkline color based on the trend direction AND whether
    // this metric has inverted sentiment.
    //
    // For normal metrics (Total Value, Daily Return, Sharpe Ratio):
    //   trend up   → green (#10B981)
    //   trend down → red   (#EF4444)
    //
    // For inverted metrics (Volatility, VaR):
    //   trend up   → red   (#EF4444)   ← higher is worse
    //   trend down → green (#10B981)   ← lower is better
    const sparklineColor = (() => {
        if (!sparklineData || sparklineData.length < 2) return "#3B82F6";
        const trendUp = sparklineData[sparklineData.length - 1] > sparklineData[0];
        if (invertSentiment) {
            return trendUp ? "#EF4444" : "#10B981";
        }
        return trendUp ? "#10B981" : "#EF4444";
    })();

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

                {/* FIX: Pass the computed colour down to Sparkline */}
                {sparklineData && (
                    <Sparkline
                        data={sparklineData}
                        width={72}
                        height={28}
                        color={sparklineColor}
                    />
                )}
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