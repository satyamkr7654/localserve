import { BookingWorkflow } from "./booking-workflow";

export const metadata = { title: "Booking workflow" };

export default async function BookingDetail({ params }: { params: Promise<{ bookingId: string }> }) {
  const { bookingId } = await params;
  return <BookingWorkflow bookingId={bookingId} />;
}
