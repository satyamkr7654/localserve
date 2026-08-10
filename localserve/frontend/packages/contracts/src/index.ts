import { z } from "zod";

export const bookingStatuses = [
  "CREATED", "SEARCHING_PROVIDERS", "PROVIDERS_FOUND", "PROVIDER_SELECTED",
  "PAYMENT_PENDING", "PAYMENT_COMPLETED", "PROVIDER_ASSIGNED", "PROVIDER_ON_THE_WAY",
  "PROVIDER_ARRIVED", "START_OTP_PENDING", "IN_PROGRESS", "COMPLETION_PENDING",
  "CUSTOMER_CONFIRMATION_PENDING", "COMPLETED", "DISPUTED", "CANCELLED", "REFUNDED", "CLOSED"
] as const;
export const bookingStatusSchema = z.enum(bookingStatuses);
export type BookingStatus = z.infer<typeof bookingStatusSchema>;

export const moneySchema = z.object({ amountMinor: z.int().safe(), currency: z.string().length(3) });
export type Money = z.infer<typeof moneySchema>;

export const providerCardSchema = z.object({
  id: z.uuid(), name: z.string().min(1).max(120), initials: z.string().min(1).max(3),
  service: z.string().min(1).max(120), rating: z.number().min(0).max(5), reviewCount: z.int().nonnegative(),
  experienceYears: z.int().nonnegative(), distanceKm: z.number().nonnegative(), etaMinutes: z.int().positive(),
  priceFrom: moneySchema, verified: z.boolean(), available: z.boolean()
});
export type ProviderCard = z.infer<typeof providerCardSchema>;

export const bookingSummarySchema = z.object({
  id: z.uuid(), serviceName: z.string(), providerName: z.string().nullable(), status: bookingStatusSchema,
  scheduledAt: z.iso.datetime(), amount: moneySchema, etaMinutes: z.int().positive().nullable(), version: z.int().nonnegative()
});
export type BookingSummary = z.infer<typeof bookingSummarySchema>;

export const bookingDraftSchema = z.object({
  serviceId: z.string().min(2, "Choose a service").max(80),
  bookingType: z.enum(["INSTANT", "SCHEDULED", "EMERGENCY"]),
  addressLine: z.string().trim().min(10, "Enter a complete service address").max(240),
  scheduledAt: z.string().optional(),
  notes: z.string().trim().max(500, "Notes must be 500 characters or fewer").optional(),
}).superRefine((value, context) => {
  if (value.bookingType === "SCHEDULED" && !value.scheduledAt) {
    context.addIssue({ code: "custom", path: ["scheduledAt"], message: "Choose a date and time" });
  }
});
export type BookingDraft = z.infer<typeof bookingDraftSchema>;

export const dashboardSummarySchema = z.object({
  customerName: z.string(), activeBooking: bookingSummarySchema.nullable(), unreadNotifications: z.int().nonnegative(),
  walletBalance: moneySchema, providers: z.array(providerCardSchema)
});
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;

export const providerDashboardSchema = z.object({
  providerName: z.string(), online: z.boolean(), verificationStatus: z.enum(["APPROVED", "UNDER_REVIEW", "SUSPENDED"]),
  todayEarnings: moneySchema, weeklyEarnings: moneySchema, acceptanceRate: z.number().min(0).max(100),
  completionRate: z.number().min(0).max(100), averageRating: z.number().min(0).max(5), pendingRequests: z.int().nonnegative()
});
export type ProviderDashboard = z.infer<typeof providerDashboardSchema>;

export const adminDashboardSchema = z.object({
  grossTransactionValue: moneySchema, platformRevenue: moneySchema, activeBookings: z.int().nonnegative(),
  onlineProviders: z.int().nonnegative(), openDisputes: z.int().nonnegative(), pendingVerifications: z.int().nonnegative(),
  completionRate: z.number().min(0).max(100), paymentSuccessRate: z.number().min(0).max(100)
});
export type AdminDashboard = z.infer<typeof adminDashboardSchema>;

export type ApiEnvelope<T> = { data: T; meta: { correlationId: string; timestamp: string; nextCursor?: string } };
export type ApiProblem = { type: string; title: string; status: number; code: string; detail: string; correlationId?: string };

export type Phase8Service = {
  id: string; code: string; name: string; baseAmountMinor: number; currency: string;
};

export type Phase8Quote = {
  id: string; serviceId: string; serviceCode: string; serviceName: string;
  bookingType: "INSTANT" | "SCHEDULED" | "EMERGENCY"; serviceZoneId: string;
  amountMinor: number; currency: string; expiresAt: string;
};

export type Phase8Booking = {
  id: string; serviceId: string; serviceCode: string; serviceName: string;
  bookingType: "INSTANT" | "SCHEDULED" | "EMERGENCY"; serviceZoneId: string;
  address: string; problemDescription: string; expectedAmountMinor: number; currency: string;
  status: BookingStatus; version: number; selectedProviderId: string | null;
  selectedProviderName: string | null; createdAt: string; updatedAt: string;
};

export type Phase8Offer = {
  id: string; bookingId: string; providerId: string; providerName: string;
  serviceCode: string; serviceName: string; serviceZoneId: string; problemDescription: string;
  expectedAmountMinor: number; currency: string; status: string;
  estimatedAmountMinor: number | null; etaMinutes: number | null; note: string;
  expiresAt: string; updatedAt: string;
};

export type Phase8Provider = {
  id: string; displayName: string; businessDisplayName: string | null;
  serviceZoneId: string | null; serviceCodes: string[]; onboardingStatus: string;
  online: boolean; capacity: number; updatedAt: string | null;
};

export type Phase8Challenge = {
  challengeId: string; purpose: "BOOKING_START" | "BOOKING_COMPLETION";
  expiresAt: string; deliveryChannel: string; bookingVersion: number;
};
