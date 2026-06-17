import React, { ButtonHTMLAttributes, useState, forwardRef } from "react";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "danger-ghost";
type ButtonSize = "sm" | "md" | "lg";

interface ProbityButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  iconLeft?: React.ReactNode;
  iconRight?: React.ReactNode;
}

const SIZE_STYLES: Record<ButtonSize, React.CSSProperties> = {
  sm: { paddingInline: "12px", paddingBlock: "6px", fontSize: "12px", gap: "6px", borderRadius: "5px" },
  md: { paddingInline: "16px", paddingBlock: "8px", fontSize: "13px", gap: "7px", borderRadius: "6px" },
  lg: { paddingInline: "20px", paddingBlock: "10px", fontSize: "14px", gap: "8px", borderRadius: "6px" },
};

const VARIANT_STYLES: Record<ButtonVariant, { base: React.CSSProperties; hover: React.CSSProperties; active: React.CSSProperties }> = {
  primary: {
    base: {
      backgroundColor: "#3B82F6",
      borderWidth: "1px",
      borderStyle: "solid",
      borderColor: "rgba(96,165,250,0.3)",
      color: "#ffffff",
      boxShadow: "0 0 0 0 rgba(59,130,246,0)",
    },
    hover: {
      backgroundColor: "#60A5FA",
      boxShadow: "0 0 16px rgba(59,130,246,0.35)",
    },
    active: {
      backgroundColor: "#2563EB",
    },
  },
  secondary: {
    base: {
      backgroundColor: "var(--pb-surface-elevated)",
      borderWidth: "1px",
      borderStyle: "solid",
      borderColor: "var(--pb-border)",
      color: "var(--pb-text-primary)",
    },
    hover: {
      backgroundColor: "#1a2640",
      borderColor: "rgba(59,130,246,0.3)",
    },
    active: {
      backgroundColor: "#0f1c35",
    },
  },
  ghost: {
    base: {
      backgroundColor: "transparent",
      borderWidth: "1px",
      borderStyle: "solid",
      borderColor: "transparent",
      color: "var(--pb-text-secondary)",
    },
    hover: {
      backgroundColor: "rgba(30,45,69,0.6)",
      borderColor: "var(--pb-border)",
      color: "var(--pb-text-primary)",
    },
    active: {
      backgroundColor: "rgba(30,45,69,0.9)",
    },
  },
  danger: {
    base: {
      backgroundColor: "rgba(239,68,68,0.1)",
      borderWidth: "1px",
      borderStyle: "solid",
      borderColor: "rgba(239,68,68,0.25)",
      color: "#EF4444",
    },
    hover: {
      backgroundColor: "rgba(239,68,68,0.18)",
      borderColor: "rgba(239,68,68,0.5)",
      boxShadow: "0 0 12px rgba(239,68,68,0.15)",
    },
    active: {
      backgroundColor: "rgba(239,68,68,0.25)",
    },
  },
  "danger-ghost": {
    base: {
      backgroundColor: "transparent",
      borderWidth: "1px",
      borderStyle: "solid",
      borderColor: "transparent",
      color: "#EF4444",
    },
    hover: {
      backgroundColor: "rgba(239,68,68,0.1)",
      borderColor: "rgba(239,68,68,0.25)",
    },
    active: {
      backgroundColor: "rgba(239,68,68,0.15)",
    },
  },
};

export const ProbityButton = forwardRef<HTMLButtonElement, ProbityButtonProps>(function ProbityButton(
  {
    variant = "primary",
    size = "md",
    loading = false,
    iconLeft,
    iconRight,
    children,
    disabled,
    style,
    ...rest
  }: ProbityButtonProps,
  ref
) {
  const [isHovered, setIsHovered] = useState(false);
  const [isActive, setIsActive] = useState(false);

  const vStyles = VARIANT_STYLES[variant];
  const sStyles = SIZE_STYLES[size];

  const combinedStyle: React.CSSProperties = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    fontFamily: "var(--font-body)",
    fontWeight: 500,
    letterSpacing: "0.01em",
    cursor: disabled || loading ? "not-allowed" : "pointer",
    transition: "all 0.15s ease",
    outline: "none",
    position: "relative",
    whiteSpace: "nowrap",
    userSelect: "none",
    opacity: disabled ? 0.45 : 1,
    ...sStyles,
    ...vStyles.base,
    ...(isHovered && !disabled && !loading ? vStyles.hover : {}),
    ...(isActive && !disabled && !loading ? vStyles.active : {}),
    ...style,
  };

  return (
    <button
      ref={ref}
      style={combinedStyle}
      disabled={disabled || loading}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => { setIsHovered(false); setIsActive(false); }}
      onMouseDown={() => setIsActive(true)}
      onMouseUp={() => setIsActive(false)}
      {...rest}
    >
      {loading ? (
        <span
          style={{
            width: "14px",
            height: "14px",
            border: "1.5px solid currentColor",
            borderTopColor: "transparent",
            borderRadius: "50%",
            animation: "pb-spin 0.7s linear infinite",
            flexShrink: 0,
          }}
        />
      ) : (
        iconLeft && <span style={{ display: "flex", alignItems: "center", flexShrink: 0 }}>{iconLeft}</span>
      )}
      {children}
      {!loading && iconRight && (
        <span style={{ display: "flex", alignItems: "center", flexShrink: 0 }}>{iconRight}</span>
      )}
      <style>{`@keyframes pb-spin{to{transform:rotate(360deg)}}`}</style>
    </button>
  );
});