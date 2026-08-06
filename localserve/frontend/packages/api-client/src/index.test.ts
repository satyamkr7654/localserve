import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { ApiClient, LocalServeApiError } from "./index";

describe("ApiClient", () => {
  it("validates successful response contracts", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ value: 7 }), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    const api = new ApiClient({ baseUrl: "https://api.example.test", fetchImplementation: fetcher });
    await expect(api.request("/api/v1/public/platform-status", { schema: z.object({ value: z.number() }) }))
      .resolves.toEqual({ value: 7 });
    expect(fetcher).toHaveBeenCalledOnce();
  });

  it("returns only safe problem details", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      code: "BOOKING.INVALID_TRANSITION", detail: "Booking transition is not allowed", correlationId: "safe-id",
    }), { status: 409 }));
    const api = new ApiClient({ baseUrl: "https://api.example.test", fetchImplementation: fetcher });
    const error = await api.request("/api/v1/customer/bookings", { schema: z.unknown() }).catch(value => value);
    expect(error).toBeInstanceOf(LocalServeApiError);
    expect(error).toMatchObject({ status: 409, code: "BOOKING.INVALID_TRANSITION", correlationId: "safe-id" });
  });
});
