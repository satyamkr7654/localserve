"use client";

import type { ApiProblem, Phase8Booking, Phase8Offer } from "@localserve/contracts";
import { Badge, BookingStatusBadge, Button, Card, CardContent, CardHeader, Input, formatMoney } from "@localserve/ui";
import { CheckCircle2, MapPin, Navigation, RefreshCw, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

export function ProviderJobs() {
  const [offers, setOffers] = useState<Phase8Offer[]>([]);
  const [bookings, setBookings] = useState<Phase8Booking[]>([]);
  const [otpByBooking, setOtpByBooking] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try { const [nextOffers, nextBookings] = await Promise.all([api<Phase8Offer[]>("/api/backend/offers"), api<Phase8Booking[]>("/api/backend/bookings")]); setOffers(nextOffers); setBookings(nextBookings); }
    catch (cause) { setError(message(cause)); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.all([api<Phase8Offer[]>("/api/backend/offers"), api<Phase8Booking[]>("/api/backend/bookings")])
      .then(([nextOffers, nextBookings]) => { if (!cancelled) { setOffers(nextOffers); setBookings(nextBookings); } })
      .catch((cause: unknown) => { if (!cancelled) setError(message(cause)); });
    return () => { cancelled = true; };
  }, []);

  async function action(path: string, body?: object) {
    setBusy(true); setError("");
    try { await api(path, { method: "POST", ...(body ? { body: JSON.stringify(body) } : {}) }); await load(); }
    catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  const pending = offers.filter((offer) => offer.status === "PENDING");
  return <div className="space-y-5">
    <div className="flex justify-end"><Button variant="secondary" disabled={busy} onClick={() => void load()}><RefreshCw className="size-4" />Refresh from API</Button></div>
    {error && <p role="alert" className="rounded-2xl bg-danger-soft p-4 text-sm font-semibold text-danger">{error}</p>}
    <Card><CardHeader eyebrow="Dispatch" title="New matching offers" action={<Badge tone={pending.length ? "warning" : "neutral"}>{pending.length}</Badge>} /><CardContent className="space-y-3">
      {!pending.length && <p className="text-sm text-muted-foreground">No pending offers. Make sure profile is approved + online before the customer creates the booking.</p>}
      {pending.map((offer) => <div key={offer.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border p-4"><div><p className="font-extrabold">{offer.serviceName}</p><p className="mt-1 text-xs text-muted-foreground"><MapPin className="mr-1 inline size-3" />{offer.serviceZoneId} · Customer budget {formatMoney({ amountMinor: offer.expectedAmountMinor, currency: offer.currency })}</p><p className="mt-2 text-sm">{offer.problemDescription}</p></div><Button disabled={busy} onClick={() => void action(`/api/backend/offers/${offer.id}/acceptances`, { estimatedAmountMinor: offer.expectedAmountMinor, etaMinutes: 30, note: "Available for this job" })}>Accept · ETA 30 min</Button></div>)}
    </CardContent></Card>
    <Card><CardHeader eyebrow="Assigned work" title="Booking workflow" action={<Badge tone={bookings.length ? "info" : "neutral"}>{bookings.length}</Badge>} /><CardContent className="space-y-4">
      {!bookings.length && <p className="text-sm text-muted-foreground">Accepted offers appear as assigned jobs after the customer selects you and confirms local test payment.</p>}
      {bookings.map((booking) => <div key={booking.id} className="space-y-4 rounded-2xl border border-border p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-extrabold">{booking.serviceName}</p><p className="mt-1 text-xs text-muted-foreground">{booking.address} · {booking.id.slice(0, 8)}</p></div><BookingStatusBadge status={booking.status} /></div>
        <div className="flex flex-wrap gap-2">
          {booking.status === "PROVIDER_ASSIGNED" && <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${booking.id}/journey-starts`)}><Navigation className="size-4" />Start journey</Button>}
          {booking.status === "PROVIDER_ON_THE_WAY" && <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${booking.id}/arrivals`)}><MapPin className="size-4" />Mark arrived</Button>}
          {(booking.status === "START_OTP_PENDING" || booking.status === "COMPLETION_PENDING") && <><Input className="max-w-44" inputMode="numeric" maxLength={6} placeholder="6-digit OTP" value={otpByBooking[booking.id] ?? ""} onChange={(event) => setOtpByBooking((current) => ({ ...current, [booking.id]: event.target.value.replace(/\D/g, "") }))} />
            <Button disabled={busy || (otpByBooking[booking.id]?.length ?? 0) !== 6} onClick={() => void action(`/api/backend/bookings/${booking.id}/${booking.status === "START_OTP_PENDING" ? "start" : "completion"}-otp-verifications`, { code: otpByBooking[booking.id] })}><ShieldCheck className="size-4" />Verify {booking.status === "START_OTP_PENDING" ? "start" : "completion"} OTP</Button></>}
          {booking.status === "IN_PROGRESS" && <Button disabled={busy} onClick={() => void action(`/api/backend/bookings/${booking.id}/completion-requests`, { afterEvidenceAcknowledged: true })}><CheckCircle2 className="size-4" />Work finished</Button>}
        </div>
      </div>)}
    </CardContent></Card>
  </div>;
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> { const headers = new Headers(init.headers); if (init.body) headers.set("Content-Type", "application/json"); const response = await fetch(path, { ...init, headers, cache: "no-store" }); const payload = await response.json().catch(() => null) as T | ApiProblem | null; if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed"); return payload as T; }
function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
