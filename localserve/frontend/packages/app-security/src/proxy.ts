import { NextResponse, type NextRequest } from "next/server";

export type AppRole = "CUSTOMER" | "PROVIDER" | "ADMIN";

const publicPaths = ["/login", "/register", "/verify-email", "/reset-password", "/oauth/google/result"];

export async function proxy(request: NextRequest, requiredRole: AppRole = "CUSTOMER") {
  const nonce = btoa(crypto.randomUUID());
  const csp = [
    "default-src 'self'", `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`,
    `style-src 'self' 'nonce-${nonce}'`, "style-src-attr 'unsafe-inline'", "img-src 'self' blob: data: https://maps.googleapis.com https://maps.gstatic.com",
    "font-src 'self'", "connect-src 'self' https://api.localserve.example https://accounts.google.com wss://api.localserve.example http://localhost:8080 ws://localhost:8080",
    "worker-src 'self' blob:", "manifest-src 'self'", "object-src 'none'", "base-uri 'self'", "form-action 'self'", "frame-ancestors 'none'",
    ...(process.env.NODE_ENV === "production" ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  let response: NextResponse;
  if (!publicPaths.some((path) => request.nextUrl.pathname.startsWith(path))) {
    const accessToken = request.cookies.get("__Host-localserve_access")?.value
      ?? request.cookies.get("localserve_access")?.value;
    const valid = accessToken ? await validateSession(accessToken, requiredRole) : false;
    if (!valid) {
      const login = new URL("/login", request.url);
      login.searchParams.set("returnTo", safeReturnTo(request.nextUrl.pathname + request.nextUrl.search));
      response = NextResponse.redirect(login);
    } else {
      response = NextResponse.next({ request: { headers: requestHeaders } });
    }
  } else {
    response = NextResponse.next({ request: { headers: requestHeaders } });
  }
  applyHeaders(response, csp);
  return response;
}

async function validateSession(accessToken: string, role: AppRole) {
  const origin = process.env.BACKEND_API_ORIGIN ?? "http://localhost:8080";
  const endpoint = role === "ADMIN" ? "/api/v1/admin/me" : "/api/v1/account";
  try {
    const response = await fetch(origin + endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store",
    });
    if (!response.ok) return false;
    const body = await response.json() as { roles?: string[]; account?: { roles?: string[] } };
    const roles = body.account?.roles ?? body.roles;
    return roles?.includes(role) ?? false;
  } catch {
    return false;
  }
}

function safeReturnTo(value: string) {
  return value.startsWith("/") && !value.startsWith("//") ? value : "/";
}

function applyHeaders(response: NextResponse, csp: string) {
  response.headers.set("Content-Security-Policy", csp);
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(self), payment=(self)");
  response.headers.set("Cross-Origin-Opener-Policy", "same-origin");
  if (process.env.NODE_ENV === "production") {
    response.headers.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
  }
}

export const config = { matcher: [{ source: "/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)", missing: [{ type: "header", key: "next-router-prefetch" }, { type: "header", key: "purpose", value: "prefetch" }] }] };
