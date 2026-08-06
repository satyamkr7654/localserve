"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { bookingDraftSchema, type BookingDraft } from "@localserve/contracts";
import { Badge, Button, Card, CardContent, CardHeader, Field, Input } from "@localserve/ui";
import { CalendarClock, MapPin, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";

const services = ["Electrician", "Plumber", "AC repair", "Cleaning", "Appliance repair", "Laptop repair"];

export function BookingDraftForm({ initialService }: { initialService?: string | undefined }) {
  const [review, setReview] = useState<BookingDraft>();
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<BookingDraft>({
    resolver: zodResolver(bookingDraftSchema),
    defaultValues: { serviceId: initialService ?? "", bookingType: "INSTANT", addressLine: "", notes: "" },
    mode: "onBlur",
  });
  const bookingType = useWatch({ control, name: "bookingType" });

  if (review) {
    return (
      <Card>
        <CardHeader eyebrow="Validated locally" title="Review your service request" action={<Badge tone="positive">Ready</Badge>} />
        <CardContent className="space-y-5">
          <dl className="grid gap-4 rounded-2xl bg-surface-subtle p-5 sm:grid-cols-2">
            <ReviewItem label="Service" value={review.serviceId} />
            <ReviewItem label="Booking type" value={labelBookingType(review.bookingType)} />
            <ReviewItem label="Address" value={review.addressLine} />
            <ReviewItem label="Schedule" value={review.scheduledAt ? new Date(review.scheduledAt).toLocaleString() : "As soon as possible"} />
          </dl>
          <div className="flex flex-wrap gap-3">
            <Button type="button" onClick={() => setReview(undefined)}>Edit request</Button>
            <Button type="button" variant="secondary" disabled>Find providers after sign-in</Button>
          </div>
          <p className="flex items-start gap-2 text-xs leading-5 text-muted-foreground">
            <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
            No payment was created. The backend will calculate eligibility and pricing, and payment is requested only after you select a provider.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader eyebrow="Request details" title="Tell us what you need" />
      <CardContent>
        <form className="space-y-5" noValidate onSubmit={handleSubmit(setReview)}>
          <Field label="Service" error={errors.serviceId?.message}>
            <select
              className="min-h-12 w-full rounded-2xl border border-border bg-surface px-4 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10"
              aria-invalid={Boolean(errors.serviceId)}
              {...register("serviceId")}
            >
              <option value="">Choose a service</option>
              {services.map((service) => <option key={service} value={service}>{service}</option>)}
            </select>
          </Field>

          <fieldset>
            <legend className="text-sm font-bold">When do you need help?</legend>
            <div className="mt-2 grid gap-3 sm:grid-cols-3">
              {(["INSTANT", "SCHEDULED", "EMERGENCY"] as const).map((value) => (
                <label key={value} className="flex min-h-12 cursor-pointer items-center gap-3 rounded-2xl border border-border bg-surface px-4 text-sm font-bold has-[:checked]:border-primary has-[:checked]:bg-primary-soft has-[:checked]:text-primary">
                  <input type="radio" value={value} {...register("bookingType")} />
                  {labelBookingType(value)}
                </label>
              ))}
            </div>
          </fieldset>

          {bookingType === "SCHEDULED" && (
            <Field label="Date and time" error={errors.scheduledAt?.message}>
              <Input type="datetime-local" aria-invalid={Boolean(errors.scheduledAt)} {...register("scheduledAt")} />
            </Field>
          )}

          <Field label="Service address" error={errors.addressLine?.message} hint="Your precise address is shared only with the confirmed provider.">
            <div className="relative">
              <MapPin className="pointer-events-none absolute left-4 top-3.5 size-5 text-muted" aria-hidden="true" />
              <Input className="pl-12" placeholder="Flat, building, street and landmark" aria-invalid={Boolean(errors.addressLine)} {...register("addressLine")} />
            </div>
          </Field>

          <Field label="Service notes (optional)" error={errors.notes?.message}>
            <textarea
              className="min-h-28 w-full rounded-2xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10"
              placeholder="Describe the issue without sharing sensitive information"
              aria-invalid={Boolean(errors.notes)}
              {...register("notes")}
            />
          </Field>

          <Button type="submit" size="lg" disabled={isSubmitting}>
            <CalendarClock className="size-5" aria-hidden="true" />
            Review request
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function ReviewItem({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-bold text-muted-foreground">{label}</dt><dd className="mt-1 text-sm font-extrabold">{value}</dd></div>;
}

function labelBookingType(value: BookingDraft["bookingType"]) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
