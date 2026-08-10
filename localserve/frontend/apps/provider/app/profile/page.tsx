import { SectionHeading } from "@localserve/ui";
import { ProviderOnboarding } from "./provider-onboarding";

export const metadata = { title: "Provider profile" };

export default function ProfilePage() {
  return <div className="space-y-6"><SectionHeading eyebrow="Professional profile" title="Onboarding and availability" description="Submit services and zone, wait for admin approval, then go online." /><ProviderOnboarding /></div>;
}
