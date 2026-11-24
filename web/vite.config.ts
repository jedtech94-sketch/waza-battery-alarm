import { defineConfig } from "vite";

export default defineConfig({
  base: "/",
  server: {
    allowedHosts: [".trycloudflare.com"],
  },
  build: {
    target: "es2022",
  },
});
