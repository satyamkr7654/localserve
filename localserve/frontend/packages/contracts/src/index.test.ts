import { describe, expect, it } from "vitest";
import { bookingDraftSchema, bookingStatuses } from "./index";

describe("bookingDraftSchema", () => {
  it("accepts a complete instant booking draft", () => {
    const parsed = bookingDraftSchema.parse({
      serviceId: "Electrician",
      bookingType: "INSTANT",
      addressLine: "221B Baker Street, Sector 62",
      notes: "Power trips when the geyser starts",
    });
    expect(parsed.bookingType).toBe("INSTANT");
  });

  it("requires a date for a scheduled request", () => {
    const result = bookingDraftSchema.safeParse({
      serviceId: "Plumber",
      bookingType: "SCHEDULED",
      addressLine: "45 Lake View Road, Bengaluru",
    });
    expect(result.success).toBe(false);
  });
});

describe("booking contract", () => {
  it("keeps the frozen state-machine status vocabulary", () => {
    expect(bookingStatuses).toHaveLength(18);
    expect(bookingStatuses).toContain("CUSTOMER_CONFIRMATION_PENDING");
    expect(bookingStatuses).toContain("DISPUTED");
  });
});
