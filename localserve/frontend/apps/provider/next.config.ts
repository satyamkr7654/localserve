import type { NextConfig } from "next";
const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  transpilePackages: ["@localserve/ui", "@localserve/app-core", "@localserve/api-client", "@localserve/contracts"],
  experimental: { cpus: 1, workerThreads: true, webpackBuildWorker: false, useTypeScriptCli: false },
};
export default config;
