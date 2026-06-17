import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

const api: AxiosInstance = axios.create({
    baseURL: 'http://localhost:8080/api/v1',
    withCredentials: true,
});

// Minimal interceptor just to bypass Axios's cross-origin restriction
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const isMutating = ['post', 'put', 'patch', 'delete'].includes(config.method?.toLowerCase() ?? '');

    if (isMutating) {
        // Read the token directly from the browser's cookie jar
        const match = document.cookie.match(new RegExp('(^|;\\s*)(XSRF-TOKEN)=([^;]*)'));
        if (match && match[3]) {
            config.headers['X-XSRF-TOKEN'] = match[3];
        }
    }
    return config;
});

export default api;