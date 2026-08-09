"use client";

import { ArrowRight, LockKeyhole, ShieldCheck, Wrench } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, Input, Field } from "./primitives";

type AuthRole = "Customer" | "Provider" | "Admin";

export function LoginPage({ role }: { role: AuthRole }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [challenge, setChallenge] = useState("");
  const [code, setCode] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError("");
    const data = new FormData(event.currentTarget);
    const body = challenge
      ? { challengeId: challenge, code, requiredRole: role.toUpperCase(), device: device() }
      : { login: data.get("login"), email: data.get("login"), password: data.get("password"),
          rememberMe: role !== "Admin" && data.get("rememberMe") === "on", requiredRole: role.toUpperCase(), device: device() };
    const response = await fetch(challenge ? "/api/auth/mfa" : "/api/auth/login", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
    }).catch(() => null);
    if (!response) { setError("Authentication is temporarily unavailable."); setBusy(false); return; }
    const result = await response.json().catch(() => ({})) as { challengeId?: string; detail?: string };
    if (response.status === 202 && result.challengeId) {
      setChallenge(result.challengeId); setBusy(false); return;
    }
    if (!response.ok) { setError(result.detail ?? "Sign-in could not be completed."); setBusy(false); return; }
    const requested = new URLSearchParams(window.location.search).get("returnTo");
    window.location.assign(requested?.startsWith("/") && !requested.startsWith("//") ? requested : "/");
  }

  async function googleSignIn() {
    setBusy(true); setError("");
    const api = process.env.NEXT_PUBLIC_API_ORIGIN ?? "http://localhost:8080";
    const response = await fetch(`${api}/api/v1/auth/oauth/google/authorization-requests`, {
      method: "POST", credentials: "include", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role: role.toUpperCase() }),
    }).catch(() => null);
    const result = response ? await response.json().catch(() => ({})) as { authorizationUrl?: string; detail?: string } : {};
    if (!response?.ok || !result.authorizationUrl) {
      setError(result.detail ?? "Google sign-in is temporarily unavailable."); setBusy(false); return;
    }
    window.location.assign(result.authorizationUrl);
  }

  return <AuthFrame role={role} title={challenge ? "Verify it’s you" : `Sign in to ${role.toLowerCase()}`}>
    <form className="space-y-4" onSubmit={submit}>
      {challenge ? <Field label="Authenticator code"><Input autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} onChange={(event) => setCode(event.target.value)} required /></Field> : <>
        <Field label={role === "Admin" ? "Admin email" : "Email or phone"}><Input name="login" type={role === "Admin" ? "email" : "text"} autoComplete="username" required /></Field>
        <Field label="Password"><Input name="password" type="password" autoComplete="current-password" minLength={12} required /></Field>
        {role !== "Admin" && <label className="flex items-center gap-2 text-sm font-semibold text-muted-foreground"><input name="rememberMe" type="checkbox" className="size-4 accent-primary" /> Keep me signed in on this device</label>}
      </>}
      {error && <p role="alert" className="rounded-xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}
      <Button type="submit" size="lg" className="w-full" disabled={busy}>{busy ? "Please wait…" : challenge ? "Verify and continue" : "Sign in"}<ArrowRight className="size-4" /></Button>
    </form>
    {!challenge && role !== "Admin" && <>
      <div className="my-5 flex items-center gap-3 text-xs font-bold uppercase tracking-wider text-muted"><span className="h-px flex-1 bg-border" />or<span className="h-px flex-1 bg-border" /></div>
      <Button variant="secondary" size="lg" className="w-full" onClick={googleSignIn} disabled={busy}>Continue with Google</Button>
      <p className="mt-5 text-center text-sm text-muted-foreground">New to LocalServe? <Link href="/register" className="font-extrabold text-primary">Create an account</Link></p>
      <p className="mt-3 text-center text-sm"><Link href="/reset-password" className="font-bold text-primary">Forgot your password?</Link></p>
    </>}
  </AuthFrame>;
}

export function RegistrationPage({ role }: { role: Exclude<AuthRole, "Admin"> }) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    const data = Object.fromEntries(new FormData(event.currentTarget));
    const body = { ...data, marketingConsent: data.marketingConsent === "on", locale: navigator.language,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, acceptedTermsVersion: "2026-08-09" };
    const response = await fetch("/api/auth/register", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }).catch(() => null);
    const result = response ? await response.json().catch(() => ({})) as { detail?: string } : {};
    if (!response?.ok) { setError(result.detail ?? "Registration could not be completed."); setBusy(false); return; }
    setMessage("Account created. Check your email to verify it before signing in."); setBusy(false);
  }
  return <AuthFrame role={role} title={`Create your ${role.toLowerCase()} account`}>
    {message ? <div className="rounded-2xl bg-success-soft p-5 text-success"><ShieldCheck className="mb-3 size-7" /><p className="font-bold">{message}</p><Link href="/login" className="mt-4 inline-block font-extrabold underline">Return to sign in</Link></div> : <form className="space-y-4" onSubmit={submit}>
      <Field label="Full name"><Input name="displayName" autoComplete="name" minLength={2} maxLength={80} required /></Field>
      {role === "Provider" && <><Field label="Business display name"><Input name="businessDisplayName" maxLength={120} required /></Field><Field label="Primary service zone"><Input name="primaryServiceZoneId" maxLength={80} required /></Field></>}
      <Field label="Email"><Input name="email" type="email" autoComplete="email" required /></Field>
      <Field label="Phone (optional)" hint="Use international format, for example +919876543210"><Input name="phone" type="tel" autoComplete="tel" pattern="\+[1-9][0-9]{7,14}" /></Field>
      <Field label="Password" hint="Use 12–128 characters and avoid breached passwords."><Input name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required /></Field>
      <label className="flex items-start gap-2 text-sm text-muted-foreground"><input name="marketingConsent" type="checkbox" className="mt-1 size-4 accent-primary" />Send me optional LocalServe updates</label>
      {error && <p role="alert" className="rounded-xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}
      <Button type="submit" size="lg" className="w-full" disabled={busy}>{busy ? "Creating account…" : "Create account"}</Button>
      <p className="text-center text-sm text-muted-foreground">Already registered? <Link href="/login" className="font-extrabold text-primary">Sign in</Link></p>
    </form>}
  </AuthFrame>;
}

export function GoogleResultPage({ role = "Customer" }: { role?: Exclude<AuthRole, "Admin"> }) {
  const [status, setStatus] = useState("Completing secure Google sign-in…");
  const router = useRouter();
  useEffect(() => {
    const api = process.env.NEXT_PUBLIC_API_ORIGIN ?? "http://localhost:8080";
    void fetch(`${api}/api/v1/auth/oauth/google/result-exchanges`, { method: "POST", credentials: "include",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ device: device() }) })
      .then(async (response) => ({ response, body: await response.json().catch(() => ({})) as { accessToken?: string; detail?: string } }))
      .then(async ({ response, body }) => {
        if (!response.ok || !body.accessToken) throw new Error(body.detail ?? "Google sign-in could not be completed.");
        const adopted = await fetch("/api/auth/adopt", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ accessToken: body.accessToken }) });
        if (!adopted.ok) throw new Error("The secure session could not be established.");
        router.replace("/");
      }).catch((error: unknown) => setStatus(error instanceof Error ? error.message : "Google sign-in could not be completed."));
  }, [router]);
  return <AuthFrame role={role} title="Google sign-in"><p role="status" className="text-sm font-semibold text-muted-foreground">{status}</p></AuthFrame>;
}

export function EmailVerificationPage({ token = "" }: { token?: string }) {
  return <TokenAction title="Verify your email" endpoint="/api/v1/auth/email-verifications" field="token" button="Verify email" initialToken={token} />;
}

export function PasswordResetPage({ token = "" }: { token?: string }) {
  return token ? <TokenAction title="Reset your password" endpoint="/api/v1/auth/password-resets" field="newPassword" button="Reset password" initialToken={token} /> : <RecoveryRequest />;
}

function RecoveryRequest() {
  const [message, setMessage] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const api = process.env.NEXT_PUBLIC_API_ORIGIN ?? "http://localhost:8080";
    await fetch(`${api}/api/v1/auth/password-recovery-requests`, { method: "POST", credentials: "include",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: data.get("email") }) }).catch(() => null);
    setMessage("If that account exists, a secure reset link is on its way.");
  }
  return <AuthFrame role="Customer" title="Reset your password"><form className="space-y-4" onSubmit={submit}>
    <Field label="Account email"><Input name="email" type="email" autoComplete="email" required /></Field>
    {message && <p role="status" className="rounded-xl bg-success-soft p-3 text-sm font-semibold text-success">{message}</p>}
    <Button type="submit" className="w-full">Send reset link</Button>
  </form></AuthFrame>;
}

function TokenAction({ title, endpoint, field, button, initialToken }: { title: string; endpoint: string; field: "token" | "newPassword"; button: string; initialToken: string }) {
  const [message, setMessage] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const token = initialToken || String(data.get("token") ?? "");
    const body = field === "token" ? { token } : { token, newPassword: data.get("newPassword") };
    const api = process.env.NEXT_PUBLIC_API_ORIGIN ?? "http://localhost:8080";
    const response = await fetch(api + endpoint, { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const result = await response.json().catch(() => ({})) as { detail?: string };
    setMessage(response.ok ? "Done. You can now sign in." : result.detail ?? "This link is invalid or expired.");
  }
  return <AuthFrame role="Customer" title={title}><form className="space-y-4" onSubmit={submit}>
    {!initialToken && <Field label="Secure token" hint="This is filled automatically when you open the link from your email."><Input name="token" autoComplete="off" required /></Field>}
    {field === "newPassword" && <Field label="New password"><Input name="newPassword" type="password" minLength={12} maxLength={128} autoComplete="new-password" required /></Field>}
    {message && <p role="status" className="rounded-xl bg-surface-subtle p-3 text-sm font-semibold">{message}</p>}
    <Button type="submit" className="w-full">{button}</Button>
  </form></AuthFrame>;
}

function AuthFrame({ role, title, children }: { role: AuthRole; title: string; children: React.ReactNode }) {
  return <div className="grid min-h-dvh place-items-center px-4 py-10"><div className="w-full max-w-md">
    <Link href="/" className="mb-7 flex items-center justify-center gap-3"><span className="grid size-11 place-items-center rounded-2xl bg-primary text-white"><Wrench className="size-5" /></span><span><strong className="block text-xl font-black">LocalServe</strong><span className="text-[.65rem] font-extrabold uppercase tracking-[.18em] text-primary">{role} portal</span></span></Link>
    <Card className="p-6 sm:p-8"><div className="mb-6"><span className="mb-3 grid size-10 place-items-center rounded-xl bg-primary-soft text-primary"><LockKeyhole className="size-5" /></span><h1 className="text-2xl font-black tracking-tight">{title}</h1><p className="mt-2 text-sm text-muted-foreground">Your session is protected with short-lived access and rotating refresh credentials.</p></div>{children}</Card>
  </div></div>;
}

function device() {
  let id = sessionStorage.getItem("localserve_device_id");
  if (!id) { id = `web-${crypto.randomUUID()}`; sessionStorage.setItem("localserve_device_id", id); }
  return { deviceId: id, deviceName: "Web browser", platform: "WEB", browserOrApp: navigator.userAgent.slice(0, 80) };
}
