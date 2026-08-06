import { Button, EmptyState } from "@localserve/ui";
import Link from "next/link";

export default function NotFound() {
  return <EmptyState title="Console page not found" description="The requested operations route does not exist or you may lack access." action={<Button asChild><Link href="/">Open overview</Link></Button>} />;
}
