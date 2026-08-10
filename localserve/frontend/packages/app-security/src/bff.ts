import { NextResponse, type NextRequest } from "next/server";
import type { AppRole } from "./proxy";

export type AuthAction = "login" | "mfa" | "register" | "refresh" | "logout" | "adopt";

export async function handleRoleApi(request: NextRequest, role: AppRole, segments: string[]) {
  if (!segments.length || segments.some((segment) => !/^[A-Za-z0-9._~-]+$/.test(segment))) {
    return NextResponse.json({ code: "REQUEST.NOT_FOUND", detail: "API route was not found" }, { status: 404 });
  }
  const accessToken = request.cookies.get("__Host-localserve_access")?.value
    ?? request.cookies.get("localserve_access")?.value;
  if (!accessToken) {
    return NextResponse.json({ code: "AUTH.REQUIRED", detail: "Sign in is required" }, { status: 401 });
  }

  const origin = process.env.BACKEND_API_ORIGIN ?? "http://localhost:8080";
  const prefix = role === "CUSTOMER" ? "/api/v1/customer" : role === "PROVIDER" ? "/api/v1/provider" : "/api/v1/admin";
  const headers = new Headers({ Authorization: `Bearer ${accessToken}` });
  for (const name of ["content-type", "idempotency-key", "x-correlation-id"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const method = request.method.toUpperCase();
  const body = method === "GET" || method === "HEAD" ? undefined : await request.arrayBuffer();
  try {
    const init: RequestInit = { method, headers, cache: "no-store", redirect: "manual" };
    if (body !== undefined) init.body = body;
    const upstream = await fetch(`${origin}${prefix}/${segments.join("/")}${request.nextUrl.search}`, init);
    const response = new NextResponse(upstream.body, { status: upstream.status });
    for (const name of ["content-type", "x-correlation-id", "location", "retry-after"]) {
      const value = upstream.headers.get(name);
      if (value) response.headers.set(name, value);
    }
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    return NextResponse.json(
      { code: "API.SERVICE_UNAVAILABLE", detail: "LocalServe API is temporarily unavailable" },
      { status: 503, headers: { "Cache-Control": "no-store" } },
    );
  }
}

export async function handleAuth(request: NextRequest, role: AppRole, action: AuthAction) {
  if (action === "adopt") return adopt(request, role);
  const origin = process.env.BACKEND_API_ORIGIN ?? "http://localhost:8080";
  const path = upstreamPath(role, action);
  const headers = new Headers({ "Content-Type": "application/json", Origin: new URL(request.url).origin });
  const cookie = request.headers.get("cookie");
  if (cookie) headers.set("Cookie", cookie);
  const csrf = request.cookies.get("__Host-localserve_csrf")?.value
    ?? request.cookies.get("localserve_csrf")?.value;
  if (csrf) headers.set("X-CSRF-Token", csrf);
  const method = action === "logout" ? "POST" : request.method;
  const body = action === "refresh" || action === "logout" ? undefined : await request.text();
  let upstream: Response;
  try {
    const init: RequestInit = { method, headers, cache: "no-store", redirect: "manual" };
    if (body !== undefined) init.body = body;
    upstream = await fetch(origin + path, init);
  } catch {
    return NextResponse.json({ code: "AUTH.SERVICE_UNAVAILABLE", detail: "Authentication is temporarily unavailable" }, { status: 503 });
  }
  const payload = upstream.status === 204 ? null : await upstream.json().catch(() => null) as Record<string, unknown> | null;
  const response = payload === null
    ? new NextResponse(null, { status: upstream.status })
    : NextResponse.json(payload, { status: upstream.status });
  copySetCookies(upstream, response);
  const access = typeof payload?.accessToken === "string" ? payload.accessToken : undefined;
  if (access) setAccessCookie(response, access);
  if (action === "logout") clearAccessCookie(response);
  response.headers.set("Cache-Control", "no-store");
  return response;
}

function upstreamPath(role: AppRole, action: Exclude<AuthAction, "adopt">) {
  const admin = role === "ADMIN";
  if (action === "login") return admin ? "/api/v1/admin/auth/password-sessions" : "/api/v1/auth/password-sessions";
  if (action === "mfa") return admin ? "/api/v1/admin/auth/mfa-verifications" : "/api/v1/auth/mfa-verifications";
  if (action === "register") return role === "PROVIDER" ? "/api/v1/auth/provider-registrations" : "/api/v1/auth/customer-registrations";
  if (action === "refresh") return admin ? "/api/v1/admin/auth/token-refreshes" : "/api/v1/auth/token-refreshes";
  return admin ? "/api/v1/admin/auth/logout" : "/api/v1/auth/logout";
}

async function adopt(request: NextRequest, role: AppRole) {
  const body = await request.json().catch(() => null) as { accessToken?: unknown } | null;
  if (typeof body?.accessToken !== "string" || !tokenHasRole(body.accessToken, role)) {
    return NextResponse.json({ code: "AUTH.INVALID_TOKEN", detail: "The session could not be adopted" }, { status: 401 });
  }
  const response = new NextResponse(null, { status: 204 });
  setAccessCookie(response, body.accessToken);
  response.headers.set("Cache-Control", "no-store");
  return response;
}

function tokenHasRole(token: string, role: AppRole) {
  try {
    const encoded = token.split(".")[1];
    if (!encoded) return false;
    const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8")) as { roles?: string[]; exp?: number };
    return payload.roles?.includes(role) === true && typeof payload.exp === "number" && payload.exp * 1000 > Date.now();
  } catch { return false; }
}

function setAccessCookie(response: NextResponse, value: string) {
  response.cookies.set(process.env.NODE_ENV === "production" ? "__Host-localserve_access" : "localserve_access", value, {
    httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 10 * 60,
  });
}

function clearAccessCookie(response: NextResponse) {
  response.cookies.delete("localserve_access");
  response.cookies.delete("__Host-localserve_access");
}

function copySetCookies(upstream: Response, response: NextResponse) {
  const values = upstream.headers.getSetCookie();
  for (const value of values) response.headers.append("Set-Cookie", value);
}
