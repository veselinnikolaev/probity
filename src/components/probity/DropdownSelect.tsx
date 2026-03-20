import React, { useState, useRef, useEffect } from "react";

interface Option {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
}

interface DropdownSelectProps {
  options: Option[];
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  label?: string;
  disabled?: boolean;
  width?: string | number;
}

export function DropdownSelect({
  options,
  value,
  onChange,
  placeholder = "Select...",
  label,
  disabled = false,
  width = "100%",
}: DropdownSelectProps) {
  const [open, setOpen] = useState(false);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((o) => o.value === value);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  const [isFocused, setIsFocused] = useState(false);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "6px", width }}>
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

      <div ref={containerRef} style={{ position: "relative" }}>
        <button
          onClick={() => !disabled && setOpen(!open)}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          disabled={disabled}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            width: "100%",
            padding: "9px 12px",
            backgroundColor: "var(--pb-surface-elevated)",
            border: `1px solid ${open || isFocused ? "#3B82F6" : "var(--pb-border)"}`,
            borderRadius: open ? "6px 6px 0 0" : "6px",
            color: selectedOption ? "var(--pb-text-primary)" : "var(--pb-text-secondary)",
            fontFamily: "var(--font-body)",
            fontSize: "13px",
            cursor: disabled ? "not-allowed" : "pointer",
            transition: "border-color 0.15s",
            outline: "none",
            boxShadow: open || isFocused ? "0 0 0 3px rgba(59,130,246,0.12)" : "none",
            opacity: disabled ? 0.5 : 1,
            textAlign: "left",
          }}
        >
          <span>{selectedOption?.label ?? placeholder}</span>
          <svg
            width="12"
            height="12"
            viewBox="0 0 24 24"
            fill="none"
            stroke="var(--pb-text-secondary)"
            strokeWidth="2"
            style={{
              transition: "transform 0.15s",
              transform: open ? "rotate(180deg)" : "rotate(0deg)",
              flexShrink: 0,
            }}
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>

        {open && (
          <div
            style={{
              position: "absolute",
              top: "100%",
              left: 0,
              right: 0,
              backgroundColor: "var(--pb-surface-elevated)",
              border: "1px solid #3B82F6",
              borderTop: "1px solid var(--pb-border)",
              borderRadius: "0 0 6px 6px",
              zIndex: 1000,
              maxHeight: "220px",
              overflowY: "auto",
              boxShadow: "0 8px 24px rgba(0,0,0,0.4)",
              animation: "pb-dropdown-in 0.12s ease-out",
            }}
          >
            {options.map((opt, i) => (
              <button
                key={opt.value}
                disabled={opt.disabled}
                onClick={() => {
                  if (!opt.disabled) {
                    onChange?.(opt.value);
                    setOpen(false);
                  }
                }}
                onMouseEnter={() => setHoveredIndex(i)}
                onMouseLeave={() => setHoveredIndex(null)}
                style={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "flex-start",
                  width: "100%",
                  padding: "9px 12px",
                  border: "none",
                  borderBottom: i < options.length - 1 ? "1px solid rgba(30,45,69,0.5)" : "none",
                  backgroundColor:
                    opt.value === value
                      ? "rgba(59,130,246,0.1)"
                      : hoveredIndex === i
                      ? "rgba(30,45,69,0.6)"
                      : "transparent",
                  cursor: opt.disabled ? "not-allowed" : "pointer",
                  opacity: opt.disabled ? 0.4 : 1,
                  transition: "background-color 0.1s",
                  outline: "none",
                  gap: "2px",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
                  <span
                    style={{
                      fontFamily: "var(--font-body)",
                      fontSize: "13px",
                      color: opt.value === value ? "#60A5FA" : "var(--pb-text-primary)",
                      fontWeight: opt.value === value ? 500 : 400,
                    }}
                  >
                    {opt.label}
                  </span>
                  {opt.value === value && (
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#60A5FA" strokeWidth="2.5">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  )}
                </div>
                {opt.description && (
                  <span
                    style={{
                      fontFamily: "var(--font-body)",
                      fontSize: "11px",
                      color: "var(--pb-text-secondary)",
                    }}
                  >
                    {opt.description}
                  </span>
                )}
              </button>
            ))}
          </div>
        )}
      </div>

      <style>{`
        @keyframes pb-dropdown-in {
          from{opacity:0;transform:translateY(-4px)}
          to{opacity:1;transform:translateY(0)}
        }
      `}</style>
    </div>
  );
}
