export const CURRENCIES = [
  { value: 'usd', label: 'USD', description: 'US Dollar' },
  { value: 'eur', label: 'EUR', description: 'Euro' },
] as const;

export const VAR_CONFIDENCE_LEVELS = [
  { value: '90', label: '90%', description: 'Lower confidence' },
  { value: '95', label: '95%', description: 'Standard confidence' },
  { value: '99', label: '99%', description: 'Higher confidence' },
] as const;

export const TIME_HORIZONS = [
  { value: '1d', label: '1 Day' },
  { value: '1w', label: '1 Week' },
  { value: '1m', label: '1 Month' },
  { value: '1y', label: '1 Year' },
] as const;
