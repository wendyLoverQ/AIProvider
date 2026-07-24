import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendTarget =
    env.VITE_BACKEND_PROXY_TARGET ||
    "http://127.0.0.1:8888";

  return {
    base: "/mobile/",
    plugins: [react()],
    resolve: {
      dedupe: ["react", "react-dom"],
    },
    server: {
      host: "0.0.0.0",
      fs: {
        allow: [".."],
      },
      proxy: {
        "/api": {
          target: backendTarget,
          changeOrigin: true,
        },
        "/ws": {
          target: backendTarget.replace(/^http/, "ws"),
          ws: true,
          changeOrigin: true,
        },
      },
    },
  };
});
