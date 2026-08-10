"use client";

import type { ApiProblem, Phase8Provider } from "@localserve/contracts";
import { Badge, Button, Card, CardContent, CardHeader, Field, Input, Toggle } from "@localserve/ui";
import { RefreshCw, Send, Wifi } from "lucide-react";
import { useCallback, useEffect, useState, type FormEvent } from "react";

const services = ["electrician", "plumber", "ac-repair", "cleaning", "painter", "mechanic", "laptop", "mobile"];

export function ProviderOnboarding() {
  const [profile, setProfile] = useState<Phase8Provider>();
  const [businessDisplayName, setBusinessDisplayName] = useState("");
  const [serviceZoneId, setServiceZoneId] = useState("noida-central");
  const [serviceCodes, setServiceCodes] = useState<string[]>(["electrician"]);
  const [capacity, setCapacity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const current = await api<Phase8Provider>("/api/backend/onboarding");
      setProfile(current);
      setBusinessDisplayName(current.businessDisplayName ?? current.displayName);
      setServiceZoneId(current.serviceZoneId ?? "noida-central");
      if (current.serviceCodes.length) setServiceCodes(current.serviceCodes);
      setCapacity(current.capacity || 1);
    } catch (cause) { setError(message(cause)); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void api<Phase8Provider>("/api/backend/onboarding")
      .then((current) => {
        if (cancelled) return;
        setProfile(current); setBusinessDisplayName(current.businessDisplayName ?? current.displayName);
        setServiceZoneId(current.serviceZoneId ?? "noida-central");
        if (current.serviceCodes.length) setServiceCodes(current.serviceCodes);
        setCapacity(current.capacity || 1);
      })
      .catch((cause: unknown) => { if (!cancelled) setError(message(cause)); });
    return () => { cancelled = true; };
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const saved = await api<Phase8Provider>("/api/backend/onboarding-submissions", { method: "POST", body: JSON.stringify({ businessDisplayName, serviceZoneId, serviceCodes, capacity }) });
      setProfile(saved); setNotice("Onboarding submitted. Admin must approve this provider before you can go online.");
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  async function setOnline(online: boolean) {
    setBusy(true); setError(""); setNotice("");
    try { setProfile(await api<Phase8Provider>("/api/backend/online-transitions", { method: "POST", body: JSON.stringify({ online }) })); }
    catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  function toggleService(code: string) { setServiceCodes((current) => current.includes(code) ? current.filter((item) => item !== code) : [...current, code]); }

  return <div className="grid gap-5 lg:grid-cols-[1fr_.75fr]">
    <Card><CardHeader eyebrow="Step 1" title="Provider capability" /><CardContent><form className="space-y-5" onSubmit={submit}>
      <Field label="Business display name"><Input required minLength={2} maxLength={120} value={businessDisplayName} onChange={(event) => setBusinessDisplayName(event.target.value)} /></Field>
      <Field label="Service zone" hint="Customer booking must use this exact zone, for example noida-central."><Input required minLength={2} maxLength={80} value={serviceZoneId} onChange={(event) => setServiceZoneId(event.target.value)} /></Field>
      <Field label="Concurrent job capacity"><Input required type="number" min={1} max={5} value={capacity} onChange={(event) => setCapacity(Number(event.target.value))} /></Field>
      <fieldset><legend className="text-sm font-bold">Services</legend><div className="mt-2 grid gap-2 sm:grid-cols-2">{services.map((code) => <label key={code} className="flex min-h-11 items-center gap-3 rounded-xl border border-border px-3 text-sm font-semibold"><input type="checkbox" checked={serviceCodes.includes(code)} onChange={() => toggleService(code)} />{code.replaceAll("-", " ")}</label>)}</div></fieldset>
      {error && <p role="alert" className="rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}{notice && <p role="status" className="rounded-2xl bg-info-soft p-3 text-sm font-semibold text-info">{notice}</p>}
      <Button type="submit" disabled={busy || !serviceCodes.length}><Send className="size-4" />{busy ? "Saving…" : "Submit for admin approval"}</Button>
    </form></CardContent></Card>
    <Card><CardHeader eyebrow="Step 2" title="Operational status" action={<Button size="sm" variant="ghost" onClick={() => void load()}><RefreshCw className="size-4" />Refresh</Button>} /><CardContent className="space-y-5">
      <div className="flex items-center justify-between rounded-2xl bg-surface-subtle p-4"><div><p className="text-xs font-bold text-muted-foreground">Review status</p><p className="mt-1 font-extrabold">{profile?.onboardingStatus ?? "Loading"}</p></div><Badge tone={profile?.onboardingStatus === "APPROVED" ? "positive" : profile?.onboardingStatus === "REJECTED" ? "danger" : "warning"}>{profile?.onboardingStatus ?? "WAIT"}</Badge></div>
      <div className="flex items-center justify-between rounded-2xl border border-border p-4"><div className="flex items-center gap-3"><Wifi className="size-5 text-primary" /><div><p className="font-extrabold">Receive job offers</p><p className="text-xs text-muted-foreground">Approval is required</p></div></div><Toggle checked={profile?.online ?? false} onChange={(value) => void setOnline(value)} label="Provider online status" /></div>
      <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground"><li>Submit this profile.</li><li>Admin approves it on port 3002.</li><li>Refresh and turn online.</li><li>Matching customer creates a new booking.</li></ol>
    </CardContent></Card>
  </div>;
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> { const headers = new Headers(init.headers); if (init.body) headers.set("Content-Type", "application/json"); const response = await fetch(path, { ...init, headers, cache: "no-store" }); const payload = await response.json().catch(() => null) as T | ApiProblem | null; if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed"); return payload as T; }
function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
