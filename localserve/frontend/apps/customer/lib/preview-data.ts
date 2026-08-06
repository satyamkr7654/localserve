import type { BookingSummary, ProviderCard } from "@localserve/contracts";

export const activeBooking = {
  id: "0191265e-8c2f-7a1b-8d90-22ac9e464001", serviceName: "AC inspection & repair", providerName: "Ravi Kumar",
  status: "PROVIDER_ON_THE_WAY", scheduledAt: "2026-08-06T13:00:00Z", amount: { amountMinor: 129900, currency: "INR" }, etaMinutes: 12, version: 8,
} satisfies BookingSummary;

export const providers = [
  { id: "0191265e-8c2f-7a1b-8d90-22ac9e464101", name: "Ravi Kumar", initials: "RK", service: "AC technician", rating: 4.9, reviewCount: 438, experienceYears: 9, distanceKm: 1.8, etaMinutes: 12, priceFrom: { amountMinor: 59900, currency: "INR" }, verified: true, available: true },
  { id: "0191265e-8c2f-7a1b-8d90-22ac9e464102", name: "Aman Verma", initials: "AV", service: "Electrician", rating: 4.8, reviewCount: 312, experienceYears: 7, distanceKm: 2.4, etaMinutes: 18, priceFrom: { amountMinor: 39900, currency: "INR" }, verified: true, available: true },
  { id: "0191265e-8c2f-7a1b-8d90-22ac9e464103", name: "Neha Singh", initials: "NS", service: "Home cleaning expert", rating: 4.9, reviewCount: 526, experienceYears: 6, distanceKm: 3.1, etaMinutes: 24, priceFrom: { amountMinor: 79900, currency: "INR" }, verified: true, available: true },
] satisfies ProviderCard[];
