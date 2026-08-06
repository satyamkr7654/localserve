import type { LucideIcon } from "lucide-react";
import { ArrowDownRight, ArrowUpRight, Inbox } from "lucide-react";
import type { PropsWithChildren, ReactNode } from "react";
import { cn } from "../lib/cn";
import { Card } from "./primitives";

export function StatCard({ label, value, detail, trend, icon: Icon }: { label: string; value: string; detail: string; trend?: "up" | "down" | "flat"; icon: LucideIcon }) {
  return <Card className="min-w-0 p-5"><div className="flex items-start justify-between gap-4"><div className="min-w-0"><p className="text-xs font-bold uppercase tracking-[.12em] text-muted-foreground">{label}</p><p className="mt-3 truncate text-2xl font-black tracking-tight">{value}</p></div><span className="grid size-11 place-items-center rounded-2xl bg-primary-soft text-primary"><Icon className="size-5" aria-hidden="true"/></span></div><p className="mt-4 flex items-center gap-1.5 text-xs text-muted-foreground">{trend === "up" && <ArrowUpRight className="size-3.5 text-success"/>}{trend === "down" && <ArrowDownRight className="size-3.5 text-danger"/>}{detail}</p></Card>;
}

export function SectionHeading({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <div className="flex flex-wrap items-end justify-between gap-4"><div>{eyebrow && <p className="mb-2 text-xs font-black uppercase tracking-[.18em] text-primary">{eyebrow}</p>}<h1 className="text-balance text-2xl font-black tracking-[-.03em] sm:text-3xl">{title}</h1>{description && <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{description}</p>}</div>{action}</div>;
}

export function EmptyState({ title, description, action, icon: Icon = Inbox }: { title: string; description: string; action?: ReactNode; icon?: LucideIcon }) {
  return <div className="grid min-h-64 place-items-center rounded-[1.5rem] border border-dashed border-border bg-surface/50 p-8 text-center"><div><span className="mx-auto grid size-14 place-items-center rounded-2xl bg-surface-subtle text-muted-foreground"><Icon className="size-6"/></span><h2 className="mt-4 font-extrabold">{title}</h2><p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-muted-foreground">{description}</p>{action && <div className="mt-5">{action}</div>}</div></div>;
}

export function DataTable({ headings, children, caption }: PropsWithChildren<{ headings: string[]; caption: string }>) {
  return <div className="overflow-x-auto"><table className="w-full min-w-[680px] border-separate border-spacing-0 text-left text-sm"><caption className="sr-only">{caption}</caption><thead><tr>{headings.map(item => <th key={item} scope="col" className="border-b border-border bg-surface-subtle/70 px-5 py-3 text-[.68rem] font-extrabold uppercase tracking-[.12em] text-muted-foreground first:rounded-tl-2xl last:rounded-tr-2xl">{item}</th>)}</tr></thead><tbody className="[&_tr:last-child_td]:border-b-0">{children}</tbody></table></div>;
}

export function TableCell({ children, className }: PropsWithChildren<{ className?: string }>) { return <td className={cn("border-b border-border/70 px-5 py-4 align-middle", className)}>{children}</td>; }
