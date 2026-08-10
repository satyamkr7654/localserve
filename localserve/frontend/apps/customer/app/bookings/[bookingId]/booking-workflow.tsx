"use client";

import type { ApiProblem, Phase8Booking, Phase8Challenge, Phase8Offer } from "@localserve/contracts";
import { Badge, BookingStatusBadge, Button, Card, CardContent, CardHeader, SectionHeading, formatDateTime, formatMoney } from "@localserve/ui";
import { CheckCircle2, Clock3, RefreshCw, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

export function BookingWorkflow({ bookingId }: { bookingId: string }) {
  const [booking, setBooking] = useState<Phase8Booking>();
  const [offers, setOffers] = useState<Phase8Offer[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const current = await api<Phase8Booking>(`/api/backend/bookings/${bookingId}`);
      setBooking(current);
      if (["PROVIDERS_FOUND", "PROVIDER_SELECTED", "PAYMENT_PENDING"].includes(current.status)) {
        setOffers(await api<Phase8Offer[]>(`/api/backend/bookings/${bookingId}/offers`));
      }
    } catch (cause) { setError(message(cause)); }
  }, [bookingId]);
  useEffect(() => {
    let cancelled = false;
    void api<Phase8Booking>(`/api/backend/bookings/${bookingId}`)
      .then(async (current) => {
        const nextOffers = ["PROVIDERS_FOUND", "PROVIDER_SELECTED", "PAYMENT_PENDING"].includes(current.status)
          ? await api<Phase8Offer[]>(`/api/backend/bookings/${bookingId}/offers`) : [];
        if (!cancelled) { setBooking(current); setOffers(nextOffers); }
      })
      .catch((cause: unknown) => { if (!cancelled) setError(message(cause)); });
    return () => { cancelled = true; };
  }, [bookingId]);

  async function action<T>(path: string, body?: object) {
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await api<T>(path, { method: "POST", ...(body ? { body: JSON.stringify(body) } : {}) });
      await load(); return result;
    } catch (cause) { setError(message(cause)); return undefined; }
    finally { setBusy(false); }
  }

  async function issueOtp(kind: "start" | "completion") {
    const challenge = await action<Phase8Challenge>(`/api/backend/bookings/${bookingId}/${kind}-otp-challenges`);
    if (challenge) setNotice(`${kind === "start" ? "Start" : "Completion"} OTP sent to your email. In local mode, open Mailpit on port 8025 and give only the 6-digit code to the provider.`);
  }

  if (!booking) return <div className="space-y-4">{error && <p role="alert" className="text-danger">{error}</p>}<Card className="p-6 text-sm text-muted-foreground">Loading booking workflow…</Card></div>;

  return <div className="space-y-6">
    <SectionHeading eyebrow={`Booking · ${booking.id.slice(0, 8)}`} title={booking.serviceName} description="Customer actions appear only when the authoritative booking state allows them." action={<BookingStatusBadge status={booking.status} />} />
    {error && <p role="alert" className="rounded-2xl bg-danger-soft p-4 text-sm font-semibold text-danger">{error}</p>}
    {notice && <p role="status" className="rounded-2xl bg-info-soft p-4 text-sm font-semibold text-info">{notice} <a className="underline" href="http://localhost:8025" target="_blank" rel="noreferrer">Open Mailpit</a></p>}

    <div className="grid gap-5 lg:grid-cols-[1.1fr_.9fr]">
      <Card><CardHeader eyebrow="Request" title="Booking details" action={<Button size="sm" variant="ghost" disabled={busy} onClick={() => void load()}><RefreshCw className="size-4" />Refresh</Button>} /><CardContent><dl className="grid gap-4 sm:grid-cols-2"><Item label="Address" value={booking.address} /><Item label="Zone" value={booking.serviceZoneId} /><Item label="Problem" value={booking.problemDescription} /><Item label="Created" value={formatDateTime(booking.createdAt)} /><Item label="Expected amount" value={formatMoney({ amountMinor: booking.expectedAmountMinor, currency: booking.currency })} /><Item label="Provider" value={booking.selectedProviderName ?? "Waiting for provider offers"} /></dl></CardContent></Card>
      <Card><CardHeader eyebrow="Next step" title={nextStep(booking.status)} /><CardContent className="space-y-4"><StatusHelp status={booking.status} />
        {booking.status === "PAYMENT_PENDING" && <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${bookingId}/local-test-payment-confirmations`)}><ShieldCheck className="size-4" />Confirm local test payment</Button>}
        {booking.status === "PROVIDER_ARRIVED" && <Button disabled={busy} onClick={() => void issueOtp("start")}><ShieldCheck className="size-4" />Send start OTP</Button>}
        {booking.status === "COMPLETION_PENDING" && <Button disabled={busy} onClick={() => void issueOtp("completion")}><ShieldCheck className="size-4" />Send completion OTP</Button>}
        {booking.status === "CUSTOMER_CONFIRMATION_PENDING" && <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${bookingId}/satisfaction-confirmations`)}><CheckCircle2 className="size-4" />Confirm I am satisfied</Button>}
      </CardContent></Card>
    </div>

    {["PROVIDERS_FOUND", "PROVIDER_SELECTED", "PAYMENT_PENDING"].includes(booking.status) && <Card><CardHeader eyebrow="Dispatch" title="Provider offers" action={<Badge tone={offers.length ? "positive" : "warning"}>{offers.length} visible</Badge>} /><CardContent className="space-y-3">
      {!offers.length && <p className="text-sm text-muted-foreground">No provider has accepted yet. Ask the provider to refresh their Jobs page and accept the offer.</p>}
      {offers.map((offer) => <div key={offer.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border p-4"><div><p className="font-extrabold">{offer.providerName}</p><p className="mt-1 text-xs text-muted-foreground">ETA {offer.etaMinutes ?? "—"} min · Estimate {offer.estimatedAmountMinor ? formatMoney({ amountMinor: offer.estimatedAmountMinor, currency: offer.currency }) : "—"}</p>{offer.note && <p className="mt-1 text-xs">{offer.note}</p>}</div>{offer.status === "ACCEPTED_BY_PROVIDER" && booking.status === "PROVIDERS_FOUND" ? <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${bookingId}/provider-selections`, { offerId: offer.id })}>Select provider</Button> : <Badge tone={offer.status === "SELECTED_BY_CUSTOMER" ? "positive" : "neutral"}>{offer.status.replaceAll("_", " ")}</Badge>}</div>)}
    </CardContent></Card>}
  </div>;
}

function StatusHelp({ status }: { status: Phase8Booking["status"] }) {
  const text: Partial<Record<Phase8Booking["status"], string>> = {
    SEARCHING_PROVIDERS: "No eligible online provider matched when this booking was created.",
    PROVIDERS_FOUND: "Wait for a provider to accept, then select one offer.", PAYMENT_PENDING: "Use the local test hold to continue; real gateway capture is Phase 9.",
    PROVIDER_ASSIGNED: "The provider can start the journey from their Jobs page.", PROVIDER_ON_THE_WAY: "Wait for the provider to mark arrival.",
    PROVIDER_ARRIVED: "Send the start OTP, then read the code from Mailpit and share it in person.", START_OTP_PENDING: "The provider must enter the start OTP.",
    IN_PROGRESS: "Service is in progress. The provider requests completion after the work.", COMPLETION_PENDING: "Send the completion OTP only after inspecting the work.",
    CUSTOMER_CONFIRMATION_PENDING: "The provider verified completion. Confirm satisfaction to complete the booking.", COMPLETED: "Workflow complete. Phase 9 will release the held payment.",
  };
  return <p className="flex gap-2 text-sm leading-6 text-muted-foreground"><Clock3 className="mt-1 size-4 shrink-0 text-primary" />{text[status] ?? "Refresh after the other participant completes their step."}</p>;
}
function nextStep(status: Phase8Booking["status"]) { return status === "COMPLETED" ? "Booking completed" : "Complete the highlighted action"; }
function Item({ label, value }: { label: string; value: string }) { return <div><dt className="text-xs font-bold text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm font-semibold">{value}</dd></div>; }
async function api<T>(path: string, init: RequestInit = {}): Promise<T> { const headers = new Headers(init.headers); if (init.body) headers.set("Content-Type", "application/json"); const response = await fetch(path, { ...init, headers, cache: "no-store" }); const payload = await response.json().catch(() => null) as T | ApiProblem | null; if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed"); return payload as T; }
function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
