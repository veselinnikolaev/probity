import React from 'react';

type AlertSeverity = 'high' | 'moderate' | 'low' | string;

interface AlertIconProps {
  severity: AlertSeverity;
}

export function AlertIcon({ severity }: AlertIconProps) {
  const baseStyle: React.CSSProperties = {
    width: 18,
    height: 18,
    flexShrink: 0,
    marginTop: 2,
  };

  switch (severity) {
    case 'high':
      return (
        <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="High severity">
          <path
            d="M8.485 3.215a1.75 1.75 0 0 1 3.03 0l6.28 10.875A1.75 1.75 0 0 1 16.28 17H3.72a1.75 1.75 0 0 1-1.515-2.91L8.485 3.215Z"
            fill="#EF4444" fillOpacity="0.15" stroke="#EF4444" strokeWidth="1.4"
          />
          <path d="M10 7.5v4" stroke="#EF4444" strokeWidth="1.6" strokeLinecap="round" />
          <circle cx="10" cy="13.5" r="0.85" fill="#EF4444" />
        </svg>
      );
    case 'moderate':
      return (
        <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="Moderate severity">
          <circle cx="10" cy="10" r="7.5" stroke="#F59E0B" strokeWidth="1.4" fill="#F59E0B" fillOpacity="0.1" />
          <path d="M10 9v5" stroke="#F59E0B" strokeWidth="1.6" strokeLinecap="round" />
          <circle cx="10" cy="6.5" r="0.85" fill="#F59E0B" />
        </svg>
      );
    case 'low':
    default:
      return (
        <svg viewBox="0 0 20 20" fill="none" style={baseStyle} aria-label="Low severity">
          <circle cx="10" cy="10" r="7.5" stroke="#10B981" strokeWidth="1.4" fill="#10B981" fillOpacity="0.1" />
          <path d="M6.5 10.5l2.5 2.5 4.5-5" stroke="#10B981" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
  }
}
