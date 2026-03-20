import React, { useState, useRef, useEffect } from "react";

interface TooltipProps {
  content: React.ReactNode;
  children: React.ReactElement;
  placement?: "top" | "bottom" | "left" | "right";
  delay?: number;
}

export function Tooltip({ content, children, placement = "top", delay = 200 }: TooltipProps) {
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const triggerRef = useRef<HTMLElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const show = () => {
    timerRef.current = setTimeout(() => setVisible(true), delay);
  };
  const hide = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setVisible(false);
  };

  useEffect(() => {
    if (!visible || !triggerRef.current || !tooltipRef.current) return;
    const tEl = triggerRef.current.getBoundingClientRect();
    const ttEl = tooltipRef.current.getBoundingClientRect();
    const gap = 8;
    let top = 0;
    let left = 0;

    switch (placement) {
      case "top":
        top = tEl.top - ttEl.height - gap;
        left = tEl.left + (tEl.width - ttEl.width) / 2;
        break;
      case "bottom":
        top = tEl.bottom + gap;
        left = tEl.left + (tEl.width - ttEl.width) / 2;
        break;
      case "left":
        top = tEl.top + (tEl.height - ttEl.height) / 2;
        left = tEl.left - ttEl.width - gap;
        break;
      case "right":
        top = tEl.top + (tEl.height - ttEl.height) / 2;
        left = tEl.right + gap;
        break;
    }

    setCoords({
      top: Math.max(8, top),
      left: Math.max(8, Math.min(left, window.innerWidth - ttEl.width - 8)),
    });
  }, [visible, placement]);

  const child = React.cloneElement(children, {
    ref: triggerRef,
    onMouseEnter: (e: React.MouseEvent) => { show(); children.props.onMouseEnter?.(e); },
    onMouseLeave: (e: React.MouseEvent) => { hide(); children.props.onMouseLeave?.(e); },
    onFocus: (e: React.FocusEvent) => { show(); children.props.onFocus?.(e); },
    onBlur: (e: React.FocusEvent) => { hide(); children.props.onBlur?.(e); },
  });

  const arrowStyle: React.CSSProperties = {
    position: "absolute",
    width: 0,
    height: 0,
  };

  const arrowDir: Record<string, React.CSSProperties> = {
    top: { bottom: "-5px", left: "50%", transform: "translateX(-50%)", borderLeft: "5px solid transparent", borderRight: "5px solid transparent", borderTop: "5px solid #131B2E" },
    bottom: { top: "-5px", left: "50%", transform: "translateX(-50%)", borderLeft: "5px solid transparent", borderRight: "5px solid transparent", borderBottom: "5px solid #131B2E" },
    left: { right: "-5px", top: "50%", transform: "translateY(-50%)", borderTop: "5px solid transparent", borderBottom: "5px solid transparent", borderLeft: "5px solid #131B2E" },
    right: { left: "-5px", top: "50%", transform: "translateY(-50%)", borderTop: "5px solid transparent", borderBottom: "5px solid transparent", borderRight: "5px solid #131B2E" },
  };

  return (
    <>
      {child}
      {visible && (
        <div
          ref={tooltipRef}
          style={{
            position: "fixed",
            top: coords.top,
            left: coords.left,
            zIndex: 10000,
            backgroundColor: "#131B2E",
            border: "1px solid #1E2D45",
            borderRadius: "6px",
            padding: "7px 10px",
            maxWidth: "240px",
            pointerEvents: "none",
            boxShadow: "0 8px 24px rgba(0,0,0,0.4), 0 0 0 1px rgba(59,130,246,0.08)",
            animation: "pb-tooltip-in 0.1s ease-out",
          }}
        >
          <div style={{ ...arrowStyle, ...arrowDir[placement] }} />
          {typeof content === "string" ? (
            <span
              style={{
                fontFamily: "var(--font-body)",
                fontSize: "12px",
                color: "var(--pb-text-primary)",
                lineHeight: 1.5,
                display: "block",
              }}
            >
              {content}
            </span>
          ) : content}
          <style>{`
            @keyframes pb-tooltip-in {
              from{opacity:0;transform:translateY(2px)}
              to{opacity:1;transform:translateY(0)}
            }
          `}</style>
        </div>
      )}
    </>
  );
}