import React, { useEffect, useRef } from "react";
import { createPortal } from "react-dom";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
}

const SIZE_WIDTHS = { sm: "380px", md: "520px", lg: "680px", xl: "860px" };

export function Modal({ open, onClose, title, subtitle, children, footer, size = "md" }: ModalProps) {
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", handleKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleKey);
      document.body.style.overflow = "";
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div
      ref={overlayRef}
      onClick={(e) => { if (e.target === overlayRef.current) onClose(); }}
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "24px",
        backgroundColor: "rgba(10,13,18,0.8)",
        backdropFilter: "blur(4px)",
        animation: "pb-modal-overlay 0.15s ease-out",
      }}
    >
      <div
        style={{
          backgroundColor: "var(--pb-surface-elevated)",
          border: "1px solid var(--pb-border)",
          borderRadius: "10px",
          width: "100%",
          maxWidth: SIZE_WIDTHS[size],
          maxHeight: "calc(100vh - 48px)",
          display: "flex",
          flexDirection: "column",
          boxShadow: "0 24px 64px rgba(0,0,0,0.6), 0 0 0 1px rgba(59,130,246,0.06)",
          animation: "pb-modal-in 0.18s ease-out",
          overflow: "hidden",
          position: "relative",
        }}
      >
        {/* Top accent line */}
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: "1px",
            background: "linear-gradient(90deg, transparent 0%, rgba(59,130,246,0.5) 50%, transparent 100%)",
          }}
        />

        {/* Header */}
        {(title || subtitle) && (
          <div
            style={{
              padding: "20px 24px 16px",
              borderBottom: "1px solid var(--pb-border)",
              display: "flex",
              alignItems: "flex-start",
              justifyContent: "space-between",
              gap: "12px",
              flexShrink: 0,
            }}
          >
            <div>
              {title && (
                <h3
                  style={{
                    fontFamily: "var(--font-display)",
                    fontSize: "16px",
                    fontWeight: 700,
                    color: "var(--pb-text-primary)",
                    margin: 0,
                  }}
                >
                  {title}
                </h3>
              )}
              {subtitle && (
                <p
                  style={{
                    fontFamily: "var(--font-body)",
                    fontSize: "12px",
                    color: "var(--pb-text-secondary)",
                    margin: "4px 0 0",
                  }}
                >
                  {subtitle}
                </p>
              )}
            </div>

            <button
              onClick={onClose}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                width: "28px",
                height: "28px",
                borderRadius: "6px",
                border: "1px solid var(--pb-border)",
                backgroundColor: "transparent",
                color: "var(--pb-text-secondary)",
                cursor: "pointer",
                flexShrink: 0,
                fontSize: "16px",
                lineHeight: 1,
                transition: "all 0.12s",
              }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.backgroundColor = "rgba(30,45,69,0.8)";
                (e.currentTarget as HTMLButtonElement).style.color = "var(--pb-text-primary)";
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.backgroundColor = "transparent";
                (e.currentTarget as HTMLButtonElement).style.color = "var(--pb-text-secondary)";
              }}
              aria-label="Close"
            >
              ×
            </button>
          </div>
        )}

        {/* Body */}
        <div
          style={{
            padding: "20px 24px",
            overflowY: "auto",
            flex: 1,
          }}
        >
          {children}
        </div>

        {/* Footer */}
        {footer && (
          <div
            style={{
              padding: "16px 24px",
              borderTop: "1px solid var(--pb-border)",
              display: "flex",
              alignItems: "center",
              justifyContent: "flex-end",
              gap: "8px",
              flexShrink: 0,
              backgroundColor: "rgba(10,13,18,0.3)",
            }}
          >
            {footer}
          </div>
        )}
      </div>

      <style>{`
        @keyframes pb-modal-overlay {
          from{opacity:0}to{opacity:1}
        }
        @keyframes pb-modal-in {
          from{opacity:0;transform:translateY(8px) scale(0.98)}
          to{opacity:1;transform:translateY(0) scale(1)}
        }
      `}</style>
    </div>,
    document.body
  );
}
