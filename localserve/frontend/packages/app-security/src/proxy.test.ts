import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { proxy } from "./proxy";

afterEach(() => vi.unstubAllEnvs());

describe("security proxy", () => {
  it("issues a per-request nonce and defensive browser headers", () => {
    const response = proxy(new NextRequest("https://app.localserve.example/bookings"));
    const csp = response.headers.get("Content-Security-Policy");

    expect(csp).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/);
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(response.headers.get("Referrer-Policy")).toBe("strict-origin-when-cross-origin");
  });

  it("limits powerful browser features", () => {
    const response = proxy(new NextRequest("https://provider.localserve.example/jobs"));
    expect(response.headers.get("Permissions-Policy")).toBe("camera=(), microphone=(), geolocation=(self), payment=(self)");
  });

  it("adds HSTS to production responses", () => {
    vi.stubEnv("NODE_ENV", "production");
    const response = proxy(new NextRequest("https://admin.localserve.example/"));
    expect(response.headers.get("Strict-Transport-Security")).toBe("max-age=63072000; includeSubDomains; preload");
  });
});
