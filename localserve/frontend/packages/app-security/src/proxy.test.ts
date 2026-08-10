import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { proxy } from "./proxy";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("security proxy", () => {
  it("issues a per-request nonce and defensive browser headers", async () => {
    const response = await proxy(new NextRequest("https://app.localserve.example/bookings"));
    const csp = response.headers.get("Content-Security-Policy");

    expect(csp).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/);
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(response.headers.get("Referrer-Policy")).toBe("strict-origin-when-cross-origin");
  });

  it("accepts the direct account response returned for customer sessions", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ roles: ["CUSTOMER"] }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    )));
    const request = new NextRequest("http://localhost:3000/", {
      headers: { cookie: "localserve_access=test-access-token" },
    });

    const response = await proxy(request, "CUSTOMER");

    expect(response.status).toBe(200);
    expect(response.headers.get("location")).toBeNull();
  });

  it("accepts the wrapped account response returned for admin sessions", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ account: { roles: ["ADMIN"] } }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    )));
    const request = new NextRequest("http://localhost:3002/", {
      headers: { cookie: "localserve_access=test-access-token" },
    });

    const response = await proxy(request, "ADMIN");

    expect(response.status).toBe(200);
    expect(response.headers.get("location")).toBeNull();
  });

  it("limits powerful browser features", async () => {
    const response = await proxy(new NextRequest("https://provider.localserve.example/jobs"));
    expect(response.headers.get("Permissions-Policy")).toBe("camera=(), microphone=(), geolocation=(self), payment=(self)");
  });

  it("adds HSTS to production responses", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const response = await proxy(new NextRequest("https://admin.localserve.example/"));
    expect(response.headers.get("Strict-Transport-Security")).toBe("max-age=63072000; includeSubDomains; preload");
  });
});
