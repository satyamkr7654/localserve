"use client";

import type { ApiProblem, Phase8Booking, Phase8Quote } from "@localserve/contracts";
import { Badge, Button, Card, CardContent, CardHeader, Field, Input, formatMoney } from "@localserve/ui";
import { CalendarClock, MapPin, ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

const services = [
  ["electrician", "Electrician"], ["plumber", "Plumber"], ["ac-repair", "AC repair"],
  ["cleaning", "Cleaning"], ["painter", "Painter"], ["mechanic", "Mechanic"],
  ["laptop", "Laptop repair"], ["mobile", "Mobile repair"],
] as const;

export function BookingDraftForm({ initialService }: { initialService?: string | undefined }) {
  const router = useRouter();
  const [serviceCode, setServiceCode] = useState(initialService ?? "");
  const [bookingType, setBookingType] = useState<"INSTANT" | "EMERGENCY">("INSTANT");
  const [serviceZoneId, setServiceZoneId] = useState("noida-central");
  const [address, setAddress] = useState("");
  const [problemDescription, setProblemDescription] = useState("");
  const [quote, setQuote] = useState<Phase8Quote>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function requestQuote(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError("");
    try {
      setQuote(await api<Phase8Quote>("/api/backend/booking-quotes", {
        method: "POST", body: JSON.stringify({ serviceCode, bookingType, serviceZoneId }),
      }));
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  async function createBooking() {
    if (!quote) return;
    setBusy(true); setError("");
    try {
      const booking = await api<Phase8Booking>("/api/backend/bookings", {
        method: "POST", body: JSON.stringify({ quoteId: quote.id, address, problemDescription }),
      });
      router.push(`/bookings/${booking.id}`);
      router.refresh();
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  if (quote) {
    return <Card>
      <CardHeader eyebrow="Server quote" title="Review and create booking" action={<Badge tone="positive">15 min quote</Badge>} />
      <CardContent className="space-y-5">
        <dl className="grid gap-4 rounded-2xl bg-surface-subtle p-5 sm:grid-cols-2">
          <ReviewItem label="Service" value={quote.serviceName} />
          <ReviewItem label="Type" value={quote.bookingType} />
          <ReviewItem label="Service zone" value={quote.serviceZoneId} />
          <ReviewItem label="Expected amount" value={formatMoney({ amountMinor: quote.amountMinor, currency: quote.currency })} />
        </dl>
        {error && <p role="alert" className="rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}
        <div className="flex flex-wrap gap-3">
          <Button type="button" variant="secondary" disabled={busy} onClick={() => { setQuote(undefined); setError(""); }}>Edit</Button>
          <Button type="button" disabled={busy} onClick={createBooking}>{busy ? "Creating…" : "Create booking & find providers"}</Button>
        </div>
        <p className="flex items-start gap-2 text-xs leading-5 text-muted-foreground">
          <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
          This creates a real booking. Only approved, online providers with the same service and zone receive an offer.
        </p>
      </CardContent>
    </Card>;
  }

  return <Card>
    <CardHeader eyebrow="Request details" title="Tell us what you need" />
    <CardContent>
      <form className="space-y-5" onSubmit={requestQuote}>
        <Field label="Service">
          <select required value={serviceCode} onChange={(event) => setServiceCode(event.target.value)} className="min-h-12 w-full rounded-2xl border border-border bg-surface px-4 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10">
            <option value="">Choose a service</option>
            {services.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </select>
        </Field>
        <fieldset>
          <legend className="text-sm font-bold">When do you need help?</legend>
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            {(["INSTANT", "EMERGENCY"] as const).map((value) => <label key={value} className="flex min-h-12 cursor-pointer items-center gap-3 rounded-2xl border border-border bg-surface px-4 text-sm font-bold has-[:checked]:border-primary has-[:checked]:bg-primary-soft has-[:checked]:text-primary"><input type="radio" value={value} checked={bookingType === value} onChange={() => setBookingType(value)} />{value === "INSTANT" ? "As soon as possible" : "Emergency"}</label>)}
          </div>
        </fieldset>
        <Field label="Service zone" hint="Provider must enter exactly the same zone during onboarding."><Input required minLength={2} maxLength={80} value={serviceZoneId} onChange={(event) => setServiceZoneId(event.target.value)} /></Field>
        <Field label="Service address" hint="Shared only with the selected provider."><div className="relative"><MapPin className="pointer-events-none absolute left-4 top-3.5 size-5 text-muted" aria-hidden="true" /><Input required minLength={5} maxLength={500} className="pl-12" placeholder="Flat, building, street and landmark" value={address} onChange={(event) => setAddress(event.target.value)} /></div></Field>
        <Field label="Problem description"><textarea required minLength={5} maxLength={2000} className="min-h-28 w-full rounded-2xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10" placeholder="Describe what needs to be fixed" value={problemDescription} onChange={(event) => setProblemDescription(event.target.value)} /></Field>
        {error && <p role="alert" className="rounded-2xl bg-danger-soft p-3 text-sm font-semibold text-danger">{error}</p>}
        <Button type="submit" size="lg" disabled={busy}><CalendarClock className="size-5" aria-hidden="true" />{busy ? "Calculating…" : "Get server quote"}</Button>
      </form>
    </CardContent>
  </Card>;
}

async function api<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, headers: { "Content-Type": "application/json" }, cache: "no-store" });
  const payload = await response.json().catch(() => null) as T | ApiProblem | null;
  if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed");
  return payload as T;
}

function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
function ReviewItem({ label, value }: { label: string; value: string }) { return <div><dt className="text-xs font-bold text-muted-foreground">{label}</dt><dd className="mt-1 text-sm font-extrabold">{value}</dd></div>; }
