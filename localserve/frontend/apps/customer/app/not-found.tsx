import { Button, EmptyState } from "@localserve/ui";
import Link from "next/link";

export default function NotFound() {
  return <EmptyState title="Page not found" description="This customer page may have moved or the link is no longer valid." action={<Button asChild><Link href="/">Return home</Link></Button>} />;
}
