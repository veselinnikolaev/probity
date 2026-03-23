import axios, { AxiosInstance } from 'axios';

const BASE_URL = 'http://localhost:8080/api/v1';

const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // browser auto-sends access_token + refresh_token cookies
});

export default api;