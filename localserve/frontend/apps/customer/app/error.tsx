"use client";

import { Button, Card } from "@localserve/ui";

export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <Card className="mx-auto max-w-xl p-8 text-center" role="alert">
      <h1 className="text-2xl font-black">We couldn’t load this page</h1>
      <p className="mt-3 text-sm leading-6 text-muted-foreground">Your booking and payment data were not changed. Check your connection and try again.</p>
      <Button className="mt-6" onClick={reset}>Try again</Button>
    </Card>
  );
}
