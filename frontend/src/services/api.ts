import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { v4 as uuidv4 } from 'uuid';

const api: AxiosInstance = axios.create({
    baseURL: 'http://localhost:8080/api/v1',
    withCredentials: true,
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const method = config.method?.toLowerCase() ?? '';
    const isMutating = ['post', 'put', 'patch', 'delete'].includes(method);

    if (isMutating) {
        // 1. Existing CSRF Handling
        const match = document.cookie.match(new RegExp('(^|;\\s*)(XSRF-TOKEN)=([^;]*)'));
        if (match && match[3]) {
            config.headers['X-XSRF-TOKEN'] = match[3];
        }

        // 2. Idempotency Handling (Only required for POST mutations)
        if (method === 'post') {
            // Only generate a key if one hasn't been manually set for retries
            if (!config.headers['Idempotency-Key']) {
                config.headers['Idempotency-Key'] = uuidv4();
            }
        }
    }
    return config;
});

// Response Interceptor for handling transparent 409 or 5xx retries
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        // If a 5xx error occurs or network drops out, retry once with the IDENTICAL key
        if ((error.code === 'ECONNABORTED' || error.response?.status >= 500) && originalRequest.method === 'post') {
            if (!originalRequest._retryCount) {
                originalRequest._retryCount = 1;
                return api(originalRequest); // Fires again carrying the exact same Idempotency-Key header
            }
        }
        return Promise.reject(error);
    }
);

export default api;// m2 path-filter test: frontend-only change (throwaway PR, do not merge)
