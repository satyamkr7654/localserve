"use client";
import { useState } from "react";
import { Badge, Toggle } from "@localserve/ui";

export function AvailabilityControl({ initialOnline }: { initialOnline: boolean }) {
  const [online, setOnline] = useState(initialOnline);
  return <div className="flex items-center gap-3"><div className="text-right"><p className="text-xs font-bold text-muted-foreground">Availability preview</p><Badge tone={online ? "positive" : "neutral"}>{online ? "Online" : "Offline"}</Badge></div><Toggle checked={online} onChange={setOnline} label={online ? "Go offline in preview" : "Go online in preview"}/></div>;
}
