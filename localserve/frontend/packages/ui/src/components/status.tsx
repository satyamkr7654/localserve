import { AlertCircle, CheckCircle2, Clock3, MapPin, Search, ShieldCheck, Wrench } from "lucide-react";
import type { BookingStatus } from "@localserve/contracts";
import { Badge } from "./primitives";

const labels: Record<BookingStatus, string> = {
  CREATED: "Created", SEARCHING_PROVIDERS: "Finding experts", PROVIDERS_FOUND: "Experts found",
  PROVIDER_SELECTED: "Expert selected", PAYMENT_PENDING: "Payment pending", PAYMENT_COMPLETED: "Payment held",
  PROVIDER_ASSIGNED: "Expert assigned", PROVIDER_ON_THE_WAY: "On the way", PROVIDER_ARRIVED: "Arrived",
  START_OTP_PENDING: "Start OTP required", IN_PROGRESS: "Service in progress", COMPLETION_PENDING: "Finishing service",
  CUSTOMER_CONFIRMATION_PENDING: "Review the work", COMPLETED: "Completed", DISPUTED: "Under review",
  CANCELLED: "Cancelled", REFUNDED: "Refunded", CLOSED: "Closed",
};

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  const tone: "danger" | "positive" | "warning" | "info" = status === "DISPUTED" || status === "CANCELLED" ? "danger" : status === "CLOSED" || status === "COMPLETED" ? "positive" : status.includes("PENDING") ? "warning" : "info";
  return <Badge tone={tone}>{labels[status]}</Badge>;
}

export const timelineIcons = { search: Search, secured: ShieldCheck, route: MapPin, work: Wrench, pending: Clock3, done: CheckCircle2, alert: AlertCircle };
