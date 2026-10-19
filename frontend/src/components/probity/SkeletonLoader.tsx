import React from "react";

interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  borderRadius?: string | number;
  style?: React.CSSProperties;
}

export function Skeleton({ width = "100%", height = 14, borderRadius = 4, style }: SkeletonProps) {
  return (
    <div
      style={{
        width,
        height,
        borderRadius,
        background: "linear-gradient(90deg, var(--pb-surface-elevated) 0%, rgba(30,45,69,0.7) 50%, var(--pb-surface-elevated) 100%)",
        backgroundSize: "200% 100%",
        animation: "pb-skeleton 1.6s ease-in-out infinite",
        flexShrink: 0,
        ...style,
      }}
    >
      <style>{`
        @keyframes pb-skeleton {
          0%{background-position:200% 0}
          100%{background-position:-200% 0}
        }
      `}</style>
    </div>
  );
}

export function StatCardSkeleton() {
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
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Skeleton width="45%" height={11} />
        <Skeleton width="60px" height={18} borderRadius={9999} />
      </div>
      <Skeleton width="65%" height={28} />
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Skeleton width="50px" height={18} borderRadius={4} />
        <Skeleton width={72} height={28} borderRadius={4} />
      </div>
    </div>
  );
}

export function TableRowSkeleton({ cols = 5 }: { cols?: number }) {
  const widths = ["28%", "12%", "14%", "14%", "12%", "10%", "10%"];
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "16px",
        padding: "14px 16px",
        borderBottom: "1px solid var(--pb-border)",
      }}
    >
      {Array.from({ length: cols }).map((_, i) => (
        <Skeleton key={i} width={widths[i] ?? "10%"} height={12} />
      ))}
    </div>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div
      style={{
        backgroundColor: "var(--pb-surface)",
        border: "1px solid var(--pb-border)",
        borderRadius: "8px",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          gap: "16px",
          padding: "10px 16px",
          borderBottom: "1px solid var(--pb-border)",
          backgroundColor: "var(--pb-surface-elevated)",
        }}
      >
        {["28%", "12%", "14%", "14%", "12%"].map((w, i) => (
          <Skeleton key={i} width={w} height={10} style={{ opacity: 0.6 }} />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <TableRowSkeleton key={i} />
      ))}
    </div>
  );
}
