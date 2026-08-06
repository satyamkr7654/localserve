import { Button, EmptyState } from "@localserve/ui";
import Link from "next/link";

export default function NotFound() {
  return <EmptyState title="Page not found" description="This provider workspace page is unavailable." action={<Button asChild><Link href="/">Open overview</Link></Button>} />;
}
