"use client";

import type { ApiProblem, Phase8Booking } from "@localserve/contracts";
import { BookingStatusBadge, Button, Card, formatDateTime, formatMoney } from "@localserve/ui";
import { CalendarClock } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

export function BookingList() {
  const [bookings, setBookings] = useState<Phase8Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try { setBookings(await get<Phase8Booking[]>("/api/backend/bookings")); }
    catch (cause) { setError(message(cause)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let cancelled = false;
    void get<Phase8Booking[]>("/api/backend/bookings")
      .then((result) => { if (!cancelled) setBookings(result); })
      .catch((cause: unknown) => { if (!cancelled) setError(message(cause)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <Card className="p-6 text-sm text-muted-foreground">Loading real bookings…</Card>;
  if (error) return <Card className="space-y-3 p-6"><p role="alert" className="text-sm font-semibold text-danger">{error}</p><Button onClick={() => { setLoading(true); setError(""); void load(); }}>Retry</Button></Card>;
  if (!bookings.length) return <Card className="space-y-4 p-6"><p className="text-sm text-muted-foreground">No booking exists for this customer yet.</p><Button asChild><Link href="/book">Create your first booking</Link></Button></Card>;

  return <div className="grid gap-4">{bookings.map((booking) => <Card key={booking.id} className="flex flex-wrap items-center justify-between gap-5 p-5">
    <div className="flex items-center gap-4"><span className="grid size-12 place-items-center rounded-2xl bg-primary-soft text-primary"><CalendarClock className="size-5" /></span><div><div className="flex flex-wrap items-center gap-2"><h2 className="font-black">{booking.serviceName}</h2><BookingStatusBadge status={booking.status} /></div><p className="mt-1 text-sm text-muted-foreground">{booking.selectedProviderName ?? "Provider not selected"} · {formatDateTime(booking.createdAt)}</p></div></div>
    <div className="flex items-center gap-4"><strong>{formatMoney({ amountMinor: booking.expectedAmountMinor, currency: booking.currency })}</strong><Button asChild variant="secondary"><Link href={`/bookings/${booking.id}`}>Open workflow</Link></Button></div>
  </Card>)}</div>;
}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(path, { cache: "no-store" });
  const payload = await response.json().catch(() => null) as T | ApiProblem | null;
  if (!response.ok) throw new Error((payload as ApiProblem | null)?.detail ?? "Request failed");
  return payload as T;
}
function message(cause: unknown) { return cause instanceof Error ? cause.message : "Request failed"; }
