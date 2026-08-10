import { SectionHeading } from "@localserve/ui";
import { ProviderVerificationQueue } from "./provider-verification-queue";

export const metadata = { title: "Provider management" };

export default function ProvidersPage() {
  return <div className="space-y-6"><SectionHeading eyebrow="Marketplace supply" title="Provider verification" description="Review real provider onboarding submissions. Approval is required before a provider can go online." /><ProviderVerificationQueue /></div>;
}
