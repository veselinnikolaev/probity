import React, { useState } from "react";
import { RiskBadge, RiskLevel } from "./RiskBadge";
import { AssetTag, AssetType } from "./AssetTag";

export interface DataRow {
  id: string;
  ticker: string;
  name: string;
  assetType: AssetType;
  price: string;
  change: string;
  changePercent: string;
  riskScore: number;
  riskLevel: RiskLevel;
  volume: string;
  marketCap?: string;
}

interface DataTableProps {
  rows: DataRow[];
  selectedIds?: string[];
  onSelect?: (id: string) => void;
  onSelectAll?: () => void;
  sortable?: boolean;
}

type SortKey = keyof DataRow | null;
type SortDir = "asc" | "desc";

const COL_HEADERS = [
  { key: "ticker", label: "Ticker / Name", width: "26%" },
  { key: "assetType", label: "Type", width: "8%", align: "center" as const },
  { key: "price", label: "Price", width: "12%", align: "right" as const },
  { key: "changePercent", label: "Change", width: "10%", align: "right" as const },
  { key: "riskLevel", label: "Risk", width: "14%", align: "center" as const },
  { key: "riskScore", label: "Score", width: "8%", align: "right" as const },
  { key: "volume", label: "Volume", width: "12%", align: "right" as const },
  { key: "marketCap", label: "Mkt Cap", width: "10%", align: "right" as const },
];

export function DataTable({ rows, selectedIds = [], onSelect, onSelectAll, sortable = true }: DataTableProps) {
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>(null);
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  const handleSort = (key: string) => {
    if (!sortable) return;
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key as SortKey);
      setSortDir("asc");
    }
  };

  const allSelected = rows.length > 0 && rows.every((r) => selectedIds.includes(r.id));

  return (
    <div
      style={{
        backgroundColor: "var(--pb-surface)",
        border: "1px solid var(--pb-border)",
        borderRadius: "8px",
        overflow: "hidden",
      }}
    >
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", minWidth: "680px" }}>
          <thead>
            <tr
              style={{
                backgroundColor: "var(--pb-surface-elevated)",
                borderBottom: "1px solid var(--pb-border)",
              }}
            >
              {onSelect && (
                <th style={{ width: "40px", padding: "10px 12px" }}>
                  <input
                    type="checkbox"
                    checked={allSelected}
                    onChange={onSelectAll}
                    style={{ accentColor: "#3B82F6", cursor: "pointer" }}
                  />
                </th>
              )}
              {COL_HEADERS.map((col) => (
                <th
                  key={col.key}
                  onClick={() => handleSort(col.key)}
                  style={{
                    width: col.width,
                    padding: "10px 12px",
                    textAlign: col.align ?? "left",
                    fontFamily: "var(--font-body)",
                    fontSize: "11px",
                    fontWeight: 500,
                    letterSpacing: "0.06em",
                    textTransform: "uppercase",
                    color: sortKey === col.key ? "#60A5FA" : "var(--pb-text-secondary)",
                    cursor: sortable ? "pointer" : "default",
                    whiteSpace: "nowrap",
                    userSelect: "none",
                    transition: "color 0.12s",
                  }}
                  onMouseEnter={(e) => {
                    if (sortable) (e.currentTarget as HTMLElement).style.color = "var(--pb-text-primary)";
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.color =
                      sortKey === col.key ? "#60A5FA" : "var(--pb-text-secondary)";
                  }}
                >
                  <span style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                    {col.label}
                    {sortable && sortKey === col.key && (
                      <span style={{ fontSize: "9px", opacity: 0.8 }}>
                        {sortDir === "asc" ? "▲" : "▼"}
                      </span>
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const isSelected = selectedIds.includes(row.id);
              const isHovered = hoveredId === row.id;
              const isPositive = !row.change.startsWith("-");

              return (
                <tr
                  key={row.id}
                  onMouseEnter={() => setHoveredId(row.id)}
                  onMouseLeave={() => setHoveredId(null)}
                  onClick={() => onSelect?.(row.id)}
                  style={{
                    borderBottom: "1px solid var(--pb-border)",
                    backgroundColor: isSelected
                      ? "rgba(59,130,246,0.06)"
                      : isHovered
                      ? "rgba(19,27,46,0.8)"
                      : "transparent",
                    cursor: onSelect ? "pointer" : "default",
                    transition: "background-color 0.1s",
                    ...(isSelected ? { boxShadow: "inset 2px 0 0 #3B82F6" } : {}),
                  }}
                >
                  {onSelect && (
                    <td style={{ padding: "12px 12px" }}>
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => onSelect(row.id)}
                        onClick={(e) => e.stopPropagation()}
                        style={{ accentColor: "#3B82F6", cursor: "pointer" }}
                      />
                    </td>
                  )}

                  {/* Ticker + Name */}
                  <td style={{ padding: "12px 12px" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span
                        style={{
                          fontFamily: "var(--font-mono)",
                          fontSize: "13px",
                          fontWeight: 500,
                          color: isSelected ? "#60A5FA" : "var(--pb-text-primary)",
                          letterSpacing: "0.02em",
                        }}
                      >
                        {row.ticker}
                      </span>
                      <span
                        style={{
                          fontFamily: "var(--font-body)",
                          fontSize: "11px",
                          color: "var(--pb-text-secondary)",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          maxWidth: "160px",
                        }}
                      >
                        {row.name}
                      </span>
                    </div>
                  </td>

                  {/* Asset Type */}
                  <td style={{ padding: "12px", textAlign: "center" }}>
                    <AssetTag type={row.assetType} size="sm" />
                  </td>

                  {/* Price */}
                  <td style={{ padding: "12px", textAlign: "right" }}>
                    <span
                      style={{
                        fontFamily: "var(--font-mono)",
                        fontSize: "13px",
                        color: "var(--pb-text-primary)",
                      }}
                    >
                      {row.price}
                    </span>
                  </td>

                  {/* Change */}
                  <td style={{ padding: "12px", textAlign: "right" }}>
                    <span
                      style={{
                        fontFamily: "var(--font-mono)",
                        fontSize: "12px",
                        fontWeight: 500,
                        color: isPositive ? "#10B981" : "#EF4444",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "3px",
                      }}
                    >
                      <span style={{ fontSize: "9px" }}>{isPositive ? "▲" : "▼"}</span>
                      {row.changePercent}
                    </span>
                  </td>

                  {/* Risk Level */}
                  <td style={{ padding: "12px", textAlign: "center" }}>
                    <RiskBadge level={row.riskLevel} size="sm" />
                  </td>

                  {/* Risk Score */}
                  <td style={{ padding: "12px", textAlign: "right" }}>
                    <span
                      style={{
                        fontFamily: "var(--font-mono)",
                        fontSize: "13px",
                        color:
                          row.riskScore >= 75
                            ? "#EF4444"
                            : row.riskScore >= 45
                            ? "#F59E0B"
                            : "#10B981",
                      }}
                    >
                      {row.riskScore}
                    </span>
                  </td>

                  {/* Volume */}
                  <td style={{ padding: "12px", textAlign: "right" }}>
                    <span
                      style={{
                        fontFamily: "var(--font-mono)",
                        fontSize: "12px",
                        color: "var(--pb-text-secondary)",
                      }}
                    >
                      {row.volume}
                    </span>
                  </td>

                  {/* Market Cap */}
                  <td style={{ padding: "12px", textAlign: "right" }}>
                    <span
                      style={{
                        fontFamily: "var(--font-mono)",
                        fontSize: "12px",
                        color: "var(--pb-text-secondary)",
                      }}
                    >
                      {row.marketCap ?? "—"}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
