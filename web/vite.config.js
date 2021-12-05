import { resolve } from 'path';

import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const backendUrl = process.env.BACKEND_URL || 'http://localhost:9090';
const fullBackEndUrl = (backendUrl.endsWith("/") ? backendUrl : backendUrl + "/") + "api/v1";

export default defineConfig({
    build: {
        outDir: resolve(__dirname, 'build/web')
    },
    define: {
        BACKEND_URL: `"${fullBackEndUrl}"`
    },
    plugins: [vue()]
});
