import React from "react";

interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description: string;
  action?: React.ReactNode;
  size?: "sm" | "md" | "lg";
}

export function EmptyState({ icon, title, description, action, size = "md" }: EmptyStateProps) {
  const paddings = { sm: "32px 24px", md: "48px 32px", lg: "64px 48px" };
  const iconSizes = { sm: "32px", md: "40px", lg: "48px" };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        padding: paddings[size],
        border: "1px dashed var(--pb-border)",
        borderRadius: "8px",
        backgroundColor: "rgba(14,20,32,0.4)",
        gap: "12px",
        width: "100%",
      }}
    >
      {icon && (
        <div
          style={{
            width: iconSizes[size],
            height: iconSizes[size],
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "var(--pb-text-muted)",
            fontSize: iconSizes[size],
            marginBottom: "4px",
          }}
        >
          {icon}
        </div>
      )}

      {!icon && (
        <div
          style={{
            width: iconSizes[size],
            height: iconSizes[size],
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            border: "1px solid var(--pb-border)",
            borderRadius: "8px",
            backgroundColor: "var(--pb-surface-elevated)",
            marginBottom: "4px",
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--pb-text-muted)" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
        <h4
          style={{
            fontFamily: "var(--font-display)",
            fontSize: size === "sm" ? "13px" : size === "md" ? "15px" : "17px",
            fontWeight: 700,
            color: "var(--pb-text-primary)",
            margin: 0,
          }}
        >
          {title}
        </h4>
        <p
          style={{
            fontFamily: "var(--font-body)",
            fontSize: size === "sm" ? "12px" : "13px",
            color: "var(--pb-text-secondary)",
            margin: 0,
            lineHeight: 1.6,
            maxWidth: "320px",
          }}
        >
          {description}
        </p>
      </div>

      {action && <div style={{ marginTop: "8px" }}>{action}</div>}
    </div>
  );
}
