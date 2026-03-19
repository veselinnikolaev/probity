import React, { useState } from "react";

interface SidebarNavItemProps {
  label: string;
  icon?: React.ReactNode;
  active?: boolean;
  badge?: string | number;
  badgeVariant?: "default" | "danger" | "warning";
  onClick?: () => void;
  indent?: boolean;
}

export function SidebarNavItem({
  label,
  icon,
  active = false,
  badge,
  badgeVariant = "default",
  onClick,
  indent = false,
}: SidebarNavItemProps) {
  const [isHovered, setIsHovered] = useState(false);

  const badgeColors: Record<string, { bg: string; text: string }> = {
    default: { bg: "rgba(59,130,246,0.15)", text: "#60A5FA" },
    danger: { bg: "rgba(239,68,68,0.15)", text: "#EF4444" },
    warning: { bg: "rgba(245,158,11,0.15)", text: "#F59E0B" },
  };

  const bc = badgeColors[badgeVariant];

  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      style={{
        display: "flex",
        alignItems: "center",
        gap: "10px",
        width: "100%",
        padding: indent ? "7px 12px 7px 32px" : "8px 12px",
        borderRadius: "6px",
        border: "none",
        cursor: "pointer",
        position: "relative",
        transition: "background-color 0.12s, color 0.12s",
        backgroundColor: active
          ? "rgba(59,130,246,0.08)"
          : isHovered
          ? "rgba(30,45,69,0.6)"
          : "transparent",
        color: active
          ? "#60A5FA"
          : isHovered
          ? "var(--pb-text-primary)"
          : "var(--pb-text-secondary)",
        outline: "none",
        textAlign: "left",
        // Glowing left border on active
        ...(active
          ? {
              boxShadow: "inset 3px 0 0 #3B82F6",
            }
          : {}),
      }}
    >
      {/* Active left border glow effect */}
      {active && (
        <div
          style={{
            position: "absolute",
            left: 0,
            top: "4px",
            bottom: "4px",
            width: "3px",
            borderRadius: "0 2px 2px 0",
            backgroundColor: "#3B82F6",
            boxShadow: "0 0 8px rgba(59,130,246,0.8), 0 0 16px rgba(59,130,246,0.3)",
          }}
        />
      )}

      {icon && (
        <span
          style={{
            display: "flex",
            alignItems: "center",
            flexShrink: 0,
            fontSize: "16px",
            opacity: active ? 1 : 0.7,
          }}
        >
          {icon}
        </span>
      )}

      <span
        style={{
          fontFamily: "var(--font-body)",
          fontSize: "13px",
          fontWeight: active ? 500 : 400,
          flex: 1,
          letterSpacing: "0.01em",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {label}
      </span>

      {badge !== undefined && (
        <span
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            fontFamily: "var(--font-mono)",
            fontSize: "10px",
            fontWeight: 500,
            paddingInline: "6px",
            paddingBlock: "1px",
            borderRadius: "9999px",
            backgroundColor: bc.bg,
            color: bc.text,
            flexShrink: 0,
          }}
        >
          {badge}
        </span>
      )}
    </button>
  );
}
