export interface PreferencesResponse {
  defaultCurrency: string;
  defaultConfidenceLevel: number;
  defaultTimeHorizon: string;
}

export interface UpdatePreferencesRequest {
  defaultCurrency: string;
  defaultConfidenceLevel: number;
  defaultTimeHorizon: string;
}
