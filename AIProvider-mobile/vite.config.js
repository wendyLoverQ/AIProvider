import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

const mobileDependency = (name) =>
  fileURLToPath(new URL(`./node_modules/${name}`, import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendTarget =
    env.VITE_BACKEND_PROXY_TARGET ||
    "http://127.0.0.1:8888";

  return {
    base: "/mobile/",
    plugins: [react()],
    resolve: {
      alias: {
        "@phosphor-icons/react": mobileDependency("@phosphor-icons/react"),
        "react-zoom-pan-pinch": mobileDependency("react-zoom-pan-pinch"),
        "react-dom": mobileDependency("react-dom"),
        react: mobileDependency("react"),
      },
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
