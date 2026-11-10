import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/** 配置开发代理及服务端静态资源输出目录。 */
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
});
