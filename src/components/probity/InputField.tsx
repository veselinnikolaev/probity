import React, { InputHTMLAttributes, useState } from "react";

type InputState = "default" | "focused" | "error" | "success" | "disabled";

interface InputFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "prefix"> {
  label?: string;
  helperText?: string;
  errorMessage?: string;
  inputState?: InputState;
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
  mono?: boolean;
}

export function InputField({
  label,
  helperText,
  errorMessage,
  inputState = "default",
  prefix,
  suffix,
  mono = false,
  style,
  ...rest
}: InputFieldProps) {
  const [isFocused, setIsFocused] = useState(false);
  const isError = inputState === "error";
  const isSuccess = inputState === "success";
  const isDisabled = inputState === "disabled" || rest.disabled;

  const effectivelyFocused = isFocused || inputState === "focused";

  const borderColor = isError
    ? "#EF4444"
    : isSuccess
    ? "#10B981"
    : effectivelyFocused
    ? "#3B82F6"
    : "var(--pb-border)";

  const boxShadow = isError
    ? "0 0 0 3px rgba(239,68,68,0.12)"
    : isSuccess
    ? "0 0 0 3px rgba(16,185,129,0.12)"
    : effectivelyFocused
    ? "0 0 0 3px rgba(59,130,246,0.12)"
    : "none";

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "6px", width: "100%" }}>
      {label && (
        <label
          style={{
            fontFamily: "var(--font-body)",
            fontSize: "12px",
            fontWeight: 500,
            color: "var(--pb-text-secondary)",
            letterSpacing: "0.04em",
          }}
        >
          {label}
        </label>
      )}

      <div
        style={{
          display: "flex",
          alignItems: "center",
          backgroundColor: isDisabled ? "rgba(14,20,32,0.5)" : "var(--pb-surface-elevated)",
          border: `1px solid ${borderColor}`,
          borderRadius: "6px",
          transition: "border-color 0.15s, box-shadow 0.15s",
          boxShadow,
          overflow: "hidden",
          opacity: isDisabled ? 0.5 : 1,
        }}
      >
        {prefix && (
          <span
            style={{
              display: "flex",
              alignItems: "center",
              paddingInline: "10px",
              color: "var(--pb-text-secondary)",
              fontSize: "13px",
              fontFamily: "var(--font-mono)",
              borderRight: "1px solid var(--pb-border)",
              height: "100%",
              alignSelf: "stretch",
              justifyContent: "center",
              backgroundColor: "rgba(30,45,69,0.3)",
              whiteSpace: "nowrap",
            }}
          >
            {prefix}
          </span>
        )}

        <input
          style={{
            flex: 1,
            padding: "9px 12px",
            background: "transparent",
            border: "none",
            outline: "none",
            color: "var(--pb-text-primary)",
            fontFamily: mono ? "var(--font-mono)" : "var(--font-body)",
            fontSize: "13px",
            fontWeight: 400,
            width: "100%",
            ...style,
          }}
          disabled={isDisabled}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          {...rest}
        />

        {suffix && (
          <span
            style={{
              display: "flex",
              alignItems: "center",
              paddingInline: "10px",
              color: "var(--pb-text-secondary)",
              fontSize: "13px",
              borderLeft: "1px solid var(--pb-border)",
              height: "100%",
              alignSelf: "stretch",
              justifyContent: "center",
              backgroundColor: "rgba(30,45,69,0.3)",
            }}
          >
            {suffix}
          </span>
        )}

        {isError && (
          <span style={{ paddingRight: "10px", color: "#EF4444", fontSize: "16px", flexShrink: 0 }}>
            ⚠
          </span>
        )}
        {isSuccess && (
          <span style={{ paddingRight: "10px", color: "#10B981", fontSize: "14px", flexShrink: 0 }}>
            ✓
          </span>
        )}
      </div>

      {isError && errorMessage && (
        <span
          style={{
            fontFamily: "var(--font-body)",
            fontSize: "11px",
            color: "#EF4444",
            display: "flex",
            alignItems: "center",
            gap: "4px",
          }}
        >
          {errorMessage}
        </span>
      )}

      {!isError && helperText && (
        <span
          style={{
            fontFamily: "var(--font-body)",
            fontSize: "11px",
            color: "var(--pb-text-secondary)",
          }}
        >
          {helperText}
        </span>
      )}
    </div>
  );
}
