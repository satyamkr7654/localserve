"use client";

import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { Search } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes, type HTMLAttributes, type InputHTMLAttributes, type PropsWithChildren, type ReactNode } from "react";
import { cn } from "../lib/cn";

const buttonVariants = cva(
  "inline-flex min-h-11 items-center justify-center gap-2 rounded-2xl px-4 text-sm font-bold transition-[background,color,box-shadow,transform] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 active:translate-y-px",
  { variants: { variant: {
    primary: "bg-primary text-primary-foreground shadow-[0_10px_30px_-12px_var(--primary)] hover:bg-primary-strong",
    secondary: "border border-border bg-surface text-foreground shadow-sm hover:bg-surface-subtle",
    ghost: "text-muted-foreground hover:bg-surface-subtle hover:text-foreground",
    danger: "bg-danger text-white hover:bg-danger/90",
  }, size: { sm: "min-h-9 rounded-xl px-3 text-xs", md: "min-h-11 px-4", lg: "min-h-13 px-6 text-base", icon: "size-11 p-0" } },
  defaultVariants: { variant: "primary", size: "md" } },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants> & { asChild?: boolean };
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant, size, asChild, type = "button", ...props }, ref,
) {
  const Component = asChild ? Slot : "button";
  return <Component ref={ref} type={asChild ? undefined : type} className={cn(buttonVariants({ variant, size }), className)} {...props} />;
});

export function Card({ children, className, ...props }: PropsWithChildren<HTMLAttributes<HTMLElement>>) {
  return <section className={cn("rounded-[1.5rem] border border-border/80 bg-surface shadow-[0_12px_40px_-28px_rgba(15,23,42,.38)]", className)} {...props}>{children}</section>;
}
export function CardHeader({ eyebrow, title, action, className }: { eyebrow?: string; title: string; action?: ReactNode; className?: string }) {
  return <header className={cn("flex items-start justify-between gap-4 px-5 pb-3 pt-5", className)}><div>{eyebrow && <p className="mb-1 text-[.68rem] font-extrabold uppercase tracking-[.18em] text-primary">{eyebrow}</p>}<h2 className="text-lg font-extrabold tracking-tight">{title}</h2></div>{action}</header>;
}
export function CardContent({ children, className }: PropsWithChildren<{ className?: string }>) { return <div className={cn("px-5 pb-5", className)}>{children}</div>; }

export function Badge({ children, tone = "neutral", className }: PropsWithChildren<{ tone?: "neutral" | "positive" | "warning" | "danger" | "info"; className?: string }>) {
  const tones = { neutral: "bg-surface-subtle text-muted-foreground", positive: "bg-success-soft text-success", warning: "bg-warning-soft text-warning", danger: "bg-danger-soft text-danger", info: "bg-info-soft text-info" };
  return <span className={cn("inline-flex min-h-6 items-center rounded-full px-2.5 py-1 text-[.68rem] font-extrabold uppercase tracking-wide", tones[tone], className)}>{children}</span>;
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className, ...props }, ref) {
  return <input ref={ref} className={cn("min-h-12 w-full rounded-2xl border border-border bg-surface px-4 text-sm outline-none transition placeholder:text-muted focus:border-primary focus:ring-4 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-60", className)} {...props} />;
});

export function Field({ label, hint, error, children }: { label: string; hint?: string | undefined; error?: string | undefined; children: ReactNode }) {
  return <div className="space-y-2"><label className="block text-sm font-bold"><span className="mb-2 block">{label}</span>{children}</label>{error ? <p role="alert" className="text-xs font-semibold text-danger">{error}</p> : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}</div>;
}

export const SearchField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function SearchField({ className, ...props }, ref) {
  return <label className={cn("flex min-h-13 items-center gap-3 rounded-2xl border border-border bg-surface px-4 shadow-sm focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/10", className)}><Search aria-hidden="true" className="size-5 text-muted"/><span className="sr-only">Search</span><input ref={ref} className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted" {...props}/><kbd className="hidden rounded-lg border border-border bg-surface-subtle px-2 py-1 text-[.65rem] font-bold text-muted-foreground sm:block">⌘ K</kbd></label>;
});

export function Avatar({ initials, size = "md", className }: { initials: string; size?: "sm" | "md" | "lg"; className?: string }) {
  return <span aria-hidden="true" className={cn("inline-grid shrink-0 place-items-center rounded-2xl bg-primary-soft font-extrabold text-primary", size === "sm" && "size-9 text-xs", size === "md" && "size-11 text-sm", size === "lg" && "size-16 text-lg", className)}>{initials}</span>;
}

export function Progress({ value, label }: { value: number; label: string }) {
  const safe = Math.min(100, Math.max(0, value));
  return <div><div className="mb-2 flex justify-between text-xs"><span className="font-semibold text-muted-foreground">{label}</span><span className="font-extrabold">{safe}%</span></div><div className="h-2 overflow-hidden rounded-full bg-surface-subtle"><div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${safe}%` }}/></div></div>;
}

export function Skeleton({ className }: { className?: string }) { return <div aria-hidden="true" className={cn("animate-pulse rounded-xl bg-surface-subtle", className)}/>; }

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (value: boolean) => void; label: string }) {
  return <button type="button" role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)} className={cn("relative h-8 w-14 rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary", checked ? "border-primary bg-primary" : "border-border bg-surface-subtle")}><span className={cn("absolute left-1 top-1 size-6 rounded-full bg-white shadow transition-transform", checked ? "translate-x-6" : "translate-x-0")}/></button>;
}
