import { Reveal, SectionHeading } from "@localserve/ui";
import { BookingDraftForm } from "./booking-draft-form";

export const metadata = { title: "Request a service" };

export default async function BookPage({ searchParams }: { searchParams: Promise<{ service?: string }> }) {
  const { service } = await searchParams;
  return (
    <Reveal className="mx-auto max-w-3xl space-y-6">
      <SectionHeading
        eyebrow="New booking"
        title="Request a trusted professional"
        description="Validate your request before the platform searches for eligible nearby providers. No payment is taken at this step."
      />
      <BookingDraftForm initialService={service} />
    </Reveal>
  );
}
