"use client";

import type { ApiProblem, Phase8Provider } from "@localserve/contracts";
import { Avatar, Badge, Button, Card, CardContent, CardHeader } from "@localserve/ui";
import { RefreshCw, ShieldCheck, ShieldX } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

export function ProviderVerificationQueue() {
  const [providers, setProviders] = useState<Phase8Provider[]>([]);
  const [busyId, setBusyId] = useState("");
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try { setProviders(await api<Phase8Provider[]>("/api/backend/verification-requests")); }
    catch (cause) { setError(message(cause)); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void api<Phase8Provider[]>("/api/backend/verification-requests")
      .then((result) => { if (!cancelled) setProviders(result); })
      .catch((cause: unknown) => { if (!cancelled) setError(message(cause)); });
    return () => { cancelled = true; };
  }, []);

  async function decide(providerId: string, approved: boolean) {
    setBusyId(providerId); setError("");
    try { await api(`/api/backend/verification-requests/${providerId}/decisions`, { method: "POST", body: JSON.stringify({ approved, reason: approved ? "Verified for Phase 8 local marketplace testing" : "Onboarding details need correction" }) }); await load(); }
    catch (cause) { setError(message(cause)); }
    finally { setBusyId(""); }
  }

  return <Card><CardHeader title="Provider accounts" action={<Button size="sm" variant="secondary" onClick={() => void load()}><RefreshCw className="size-4" />Refresh</Button>} /><CardContent className="space-y-4">
    {error && <p role="alert" className="rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}
    {!providers.length && !error && <p className="text-sm text-muted-foreground">No provider accounts found.</p>}
    {providers.map((provider) => <div key={provider.id} className="flex flex-wrap items-center gap-4 rounded-2xl border border-border p-4"><Avatar initials={initials(provider.displayName)} /><div className="min-w-52 flex-1"><p className="font-extrabold">{provider.businessDisplayName ?? provider.displayName}</p><p className="mt-1 text-xs text-muted-foreground">{provider.serviceZoneId ?? "No zone"} · {provider.serviceCodes.length ? provider.serviceCodes.join(", ") : "No services submitted"}</p></div><Badge tone={tone(provider.onboardingStatus)}>{provider.onboardingStatus}</Badge>{provider.onboardingStatus !== "DRAFT" && <div className="flex gap-2"><Button size="sm" disabled={busyId === provider.id} onClick={() => void decide(provider.id, true)}><ShieldCheck className="size-4" />Approve</Button><Button size="sm" variant="danger" disabled={busyId === provider.id} onClick={() => void decide(provider.id, false)}><ShieldX className="size-4" />Reject</Button></div>}</div>)}
  </CardContent></Card>;
}

function tone(status: string): "positive" | "warning" | "danger" | "neutral" { return status === "APPROVED" ? "positive" : status === "REJECTED" ? "danger" : status === "SUBMITTED" ? "warning" : "neutral"; }
function initials(value: string) { return value.split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase(); }
async function api<T>(path: string, init: RequestInit = {}): Promise<T> { const headers = new Headers(init.headers); if (init.body) headers.set("Content-Type", "application/json"); const response = await fetch(path, { ...init, headers, cache: "no-store" }); const payload = await response.json().catch(() => null) as T | ApiProblem | null; if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed"); return payload as T; }
function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
