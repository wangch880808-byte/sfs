import process from 'node:process';

import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        allowedHosts: true,
        proxy: {
          '/admin-api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/admin-api/, ''),
            // 开发代理目标；本机 48080 被占用时可用 NUS_DEV_API_TARGET 指向其它端口的后端
            target:
              process.env.NUS_DEV_API_TARGET ||
              'http://localhost:48080/admin-api',
          },
        },
      },
    },
  };
});
