import { SectionHeading } from "@localserve/ui";
import { BookingList } from "./booking-list";

export const metadata = { title: "Bookings" };

export default function BookingsPage() {
  return <div className="space-y-6">
    <SectionHeading eyebrow="Your activity" title="Bookings" description="These records come from the Phase 8 booking API." />
    <BookingList />
  </div>;
}
