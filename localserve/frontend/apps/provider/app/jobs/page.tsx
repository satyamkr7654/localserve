import { SectionHeading } from "@localserve/ui";
import { ProviderJobs } from "./provider-jobs";

export const metadata = { title: "Jobs" };

export default function JobsPage() {
  return <div className="space-y-6"><SectionHeading eyebrow="Operations" title="Live offers and jobs" description="Accept a customer request, travel, verify both OTPs and finish the service." /><ProviderJobs /></div>;
}
