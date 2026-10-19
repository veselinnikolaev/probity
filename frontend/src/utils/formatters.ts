/**
 * Data Formatting Utilities
 * 
 * Enforces consistent formatting rules for currency, percentages, dates, and numbers
 * across the Probity platform. All numeric data must use these formatters.
 * 
 * Reference: src/docs/GLOBAL_BEHAVIOR_RULES.md (Section 6)
 */

/**
 * Formats a number as currency ($USD)
 * 
 * Rules:
 * - Use standard format: $X,XXX.XX
 * - Abbreviate large values: $1.2M, $1.2B
 * - Display with 2 decimal places
 * 
 * @param value - Number to format
 * @param abbreviate - If true, abbreviate large values (default: true)
 * @returns Formatted currency string
 * 
 * @example
 * formatCurrency(1234.5)        // "$1,234.50"
 * formatCurrency(1000000)        // "$1.0M"
 * formatCurrency(1200000, false) // "$1,200,000.00"
 */
export function formatCurrency(value: number | null | undefined, abbreviate = true): string {
  if (value === null || value === undefined) {
    return '—';
  }

  const absValue = Math.abs(value);

  // Abbreviate large values if requested
  if (abbreviate) {
    if (absValue >= 1000000000) {
      const abbreviated = (value / 1000000000).toFixed(1);
      return `$${abbreviated}B`;
    }
    if (absValue >= 1000000) {
      const abbreviated = (value / 1000000).toFixed(1);
      return `$${abbreviated}M`;
    }
    if (absValue >= 1000) {
      const abbreviated = (value / 1000).toFixed(1);
      return `$${abbreviated}K`;
    }
  }

  // Standard currency format with Intl
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

/**
 * Formats a decimal value as a percentage
 * 
 * Rules:
 * - Display with 2 decimal places
 * - Always include % symbol
 * - Handles negative percentages (shown in red elsewhere)
 * 
 * @param value - Decimal value (0.1245 = 12.45%)
 * @returns Formatted percentage string
 * 
 * @example
 * formatPercentage(0.1245)  // "12.45%"
 * formatPercentage(0.025)   // "2.50%"
 * formatPercentage(-0.05)   // "-5.00%"
 */
export function formatPercentage(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }

  const percentage = (value * 100).toFixed(2);
  return `${percentage}%`;
}

/**
 * Formats a date in ISO 8601 format
 * 
 * Rules:
 * - Always use YYYY-MM-DD format
 * - Timezone-agnostic (local date only)
 * 
 * @param date - Date object or ISO string
 * @returns Formatted date string (YYYY-MM-DD)
 * 
 * @example
 * formatDate(new Date('2026-01-15'))  // "2026-01-15"
 * formatDate('2026-01-15T10:30:00Z')  // "2026-01-15"
 */
export function formatDate(date: Date | string | null | undefined): string {
  if (!date) {
    return '—';
  }

  const dateObj = typeof date === 'string' ? new Date(date) : date;
  return dateObj.toISOString().split('T')[0];
}

/**
 * Formats a number with comma separators
 * 
 * Rules:
 * - Use comma separators for thousands
 * - Preserve decimal places
 * 
 * @param value - Number to format
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted number string
 * 
 * @example
 * formatNumber(1234567)     // "1,234,567.00"
 * formatNumber(1234567, 0)  // "1,234,567"
 * formatNumber(123.456, 1)  // "123.5"
 */
export function formatNumber(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined) {
    return '—';
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/**
 * Formats a gain/loss value with appropriate sign and styling signal
 * 
 * Rules:
 * - Positive values: prefix with "+" (shows as green elsewhere)
 * - Negative values: prefix with "–" (shows as red elsewhere)
 * - Use monospace font (IBM Plex Mono) for display
 * 
 * @param value - Number to format
 * @param type - 'currency' or 'percentage' format
 * @returns Formatted gain/loss string
 * 
 * @example
 * formatGainLoss(5000, 'currency')    // "+$5,000.00"
 * formatGainLoss(-2450.32, 'currency') // "–$2,450.32"
 * formatGainLoss(0.1245, 'percentage') // "+12.45%"
 * formatGainLoss(-0.05, 'percentage')  // "–5.00%"
 */
export function formatGainLoss(value: number | null | undefined, type: 'currency' | 'percentage' = 'currency'): string {
  if (value === null || value === undefined) {
    return '—';
  }

  const isPositive = value > 0;
  const prefix = isPositive ? '+' : '–';

  if (type === 'currency') {
    const formatted = formatCurrency(Math.abs(value), false);
    return prefix + formatted;
  }

  if (type === 'percentage') {
    const formatted = formatPercentage(Math.abs(value));
    return prefix + formatted;
  }

  return '—';
}

/**
 * Handles missing or null/undefined values with em-dash
 * 
 * Rules:
 * - Use "—" (em-dash) for missing data, not "N/A", "null", or blank
 * - Pair with muted text color for visual distinction
 * - Used in tables, forms, and detail views
 * 
 * @param value - Value to check
 * @returns Original value or "—" if falsy
 * 
 * @example
 * formatMissing(null)           // "—"
 * formatMissing(undefined)      // "—"
 * formatMissing("Acme Corp")    // "Acme Corp"
 * formatMissing(0)              // "0" (zero is not missing)
 */
export function formatMissing<T>(value: T | null | undefined): T | string {
  // Important: 0 is NOT considered missing
  if (value === null || value === undefined) {
    return '—';
  }
  
  // Empty strings are missing
  if (typeof value === 'string' && value.trim() === '') {
    return '—';
  }

  return value;
}

/**
 * Truncates text with ellipsis and returns full text for tooltip
 * 
 * Rules:
 * - Truncate long text at specified character limit
 * - Add ellipsis (…) to indicate truncation
 * - Return full text for use in hover tooltips
 * 
 * Recommended limits:
 * - Portfolio names: 20 characters
 * - Asset symbols: 10 characters
 * - Asset descriptions: 50 characters
 * - Notes/comments: 100 characters
 * 
 * @param text - Text to potentially truncate
 * @param maxLength - Maximum character length before truncation
 * @returns Object with truncated text and full text
 * 
 * @example
 * truncateText("Acme Corporation Long Fund Name", 20)
 * // { truncated: "Acme Corporation L…", full: "Acme Corporation Long Fund Name" }
 */
export function truncateText(text: string | null | undefined, maxLength: number) {
  if (!text) {
    return { truncated: '—', full: '—' };
  }

  if (text.length <= maxLength) {
    return { truncated: text, full: text };
  }

  return {
    truncated: text.substring(0, maxLength) + '…',
    full: text,
  };
}

/**
 * Formats a zero value consistently
 * 
 * Rules:
 * - Display "0.00" or "0%" to distinguish from missing data
 * - Never display blank or null for zero values
 * 
 * @param value - Number value
 * @param type - Format type: 'currency', 'percentage', or 'number'
 * @returns Formatted zero string
 * 
 * @example
 * formatZero(0, 'currency')    // "$0.00"
 * formatZero(0, 'percentage')  // "0.00%"
 * formatZero(0, 'number')      // "0.00"
 */
export function formatZero(value: number | null | undefined, type: 'currency' | 'percentage' | 'number' = 'currency'): string {
  if (value !== 0 && !value) {
    return '—';
  }

  switch (type) {
    case 'currency':
      return formatCurrency(0, false);
    case 'percentage':
      return formatPercentage(0);
    case 'number':
    default:
      return formatNumber(0);
  }
}

/**
 * Determines text color and icon for a numeric value's semantic meaning
 * 
 * Rules:
 * - Positive numbers (returns, gains): green
 * - Negative numbers (losses, risks): red
 * - Zero: neutral (primary text color)
 * - Never rely on color alone; pair with icon/text
 * 
 * @param value - Numeric value to evaluate
 * @returns Object with color variable and icon type
 * 
 * @example
 * getSemanticColor(5000)   // { color: 'var(--pb-success)', icon: 'up' }
 * getSemanticColor(-2450)  // { color: 'var(--pb-danger)', icon: 'down' }
 * getSemanticColor(0)      // { color: 'var(--pb-text-primary)', icon: 'dash' }
 */
export function getSemanticColor(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return { color: 'var(--pb-text-muted)', icon: 'dash' as const };
  }

  if (value > 0) {
    return { color: 'var(--pb-success)', icon: 'up' as const };
  }

  if (value < 0) {
    return { color: 'var(--pb-danger)', icon: 'down' as const };
  }

  return { color: 'var(--pb-text-primary)', icon: 'dash' as const };
}

/**
 * Helper to determine if a value should be displayed in monospace font
 * 
 * All numeric data should display in IBM Plex Mono (--font-mono)
 * 
 * @param value - Value to check
 * @returns true if value should use monospace font
 * 
 * @example
 * shouldUseMonospace('$1,234.50')      // true
 * shouldUseMonospace('12.45%')         // true
 * shouldUseMonospace('Acme Corp')      // false
 */
export function shouldUseMonospace(value: string | null | undefined): boolean {
  if (!value) return false;
  
  // Check if value looks like a number, currency, or percentage
  const numericPatterns = [
    /^\$[\d,]+\.\d{2}/, // Currency
    /^\d+(\.\d+)?%?/, // Numbers and percentages
    /^[\d,]+\.\d{2}/, // Formatted numbers
    /^–?\$/, // Negative currency
    /^[+–]/, // Positive/negative indicator
  ];

  return numericPatterns.some(pattern => pattern.test(value));
}
