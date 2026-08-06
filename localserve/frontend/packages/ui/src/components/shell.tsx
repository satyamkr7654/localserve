"use client";

import {
  Bell,
  BriefcaseBusiness,
  CalendarDays,
  CircleUserRound,
  ClipboardList,
  HandCoins,
  Home,
  LayoutDashboard,
  Menu,
  Moon,
  Search,
  Settings,
  ShieldCheck,
  Sun,
  Users,
  WalletCards,
  Wrench,
  X,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTheme } from "next-themes";
import { useState, useSyncExternalStore, type PropsWithChildren } from "react";
import { cn } from "../lib/cn";
import { Avatar, Button } from "./primitives";

export type NavIcon =
  | "home"
  | "search"
  | "bookings"
  | "profile"
  | "jobs"
  | "earnings"
  | "calendar"
  | "dashboard"
  | "users"
  | "finance"
  | "disputes"
  | "settings";

export type NavItem = { href: string; label: string; icon: NavIcon };

const icons = {
  home: Home,
  search: Search,
  bookings: ClipboardList,
  profile: CircleUserRound,
  jobs: BriefcaseBusiness,
  earnings: HandCoins,
  calendar: CalendarDays,
  dashboard: LayoutDashboard,
  users: Users,
  finance: WalletCards,
  disputes: ShieldCheck,
  settings: Settings,
};

type RoleShellProps = PropsWithChildren<{
  role: "Customer" | "Provider" | "Admin";
  person: string;
  initials: string;
  navigation: NavItem[];
}>;

export function RoleShell({ role, person, initials, navigation, children }: RoleShellProps) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <a href="#main-content" className="skip-link">Skip to content</a>
      {open && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-slate-950/35 backdrop-blur-sm lg:hidden"
          onClick={() => setOpen(false)}
        />
      )}

      <aside className={cn(
        "fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-border bg-surface p-4 transition-transform lg:translate-x-0",
        open ? "translate-x-0" : "-translate-x-full",
      )}>
        <div className="flex h-16 items-center justify-between px-2">
          <Link
            href={navigation[0]?.href ?? "/"}
            className="flex items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            onClick={() => setOpen(false)}
          >
            <span className="grid size-10 place-items-center rounded-2xl bg-primary text-white shadow-lg">
              <Wrench className="size-5" aria-hidden="true" />
            </span>
            <span>
              <strong className="block text-lg font-black tracking-tight">LocalServe</strong>
              <span className="block text-[.65rem] font-extrabold uppercase tracking-[.18em] text-primary">
                {role} portal
              </span>
            </span>
          </Link>
          <Button
            className="lg:hidden"
            variant="ghost"
            size="icon"
            onClick={() => setOpen(false)}
            aria-label="Close menu"
          >
            <X className="size-5" aria-hidden="true" />
          </Button>
        </div>

        <nav aria-label={`${role} navigation`} className="mt-6 flex-1 space-y-1">
          {navigation.map((item) => {
            const Icon = icons[item.icon];
            const active = item.href === "/" ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                onClick={() => setOpen(false)}
                className={cn(
                  "flex min-h-12 items-center gap-3 rounded-2xl px-4 text-sm font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                  active
                    ? "bg-primary-soft text-primary"
                    : "text-muted-foreground hover:bg-surface-subtle hover:text-foreground",
                )}
              >
                <Icon className="size-5" aria-hidden="true" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="rounded-2xl border border-border bg-surface-subtle p-3">
          <div className="flex items-center gap-3">
            <Avatar initials={initials} />
            <div className="min-w-0">
              <p className="truncate text-sm font-extrabold">{person}</p>
              <p className="text-xs text-muted-foreground">Secure session</p>
            </div>
          </div>
        </div>
      </aside>

      <div className="lg:pl-72">
        <header className="sticky top-0 z-30 flex h-18 items-center justify-between border-b border-border/80 bg-background/85 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
          <Button
            className="lg:hidden"
            variant="ghost"
            size="icon"
            onClick={() => setOpen(true)}
            aria-label="Open menu"
          >
            <Menu className="size-5" aria-hidden="true" />
          </Button>
          <p className="hidden text-sm font-bold text-muted-foreground lg:block">{greeting()}</p>
          <div className="ml-auto flex items-center gap-2">
            <ConnectionIndicator />
            <ThemeToggle />
            <Button variant="ghost" size="icon" aria-label="Notifications">
              <Bell className="size-5" aria-hidden="true" />
              <span aria-hidden="true" className="absolute ml-[1.4rem] mt-[-1.5rem] size-2 rounded-full bg-danger ring-2 ring-background" />
            </Button>
          </div>
        </header>
        <main id="main-content" className="mx-auto w-full max-w-[1540px] px-4 pb-28 pt-6 sm:px-6 lg:px-8 lg:pb-10">
          {children}
        </main>
      </div>

      <nav
        aria-label={`${role} mobile navigation`}
        className="fixed inset-x-3 bottom-3 z-30 grid grid-cols-4 rounded-[1.35rem] border border-border bg-surface/95 p-2 shadow-2xl backdrop-blur-xl lg:hidden"
      >
        {navigation.slice(0, 4).map((item) => {
          const Icon = icons[item.icon];
          const active = item.href === "/" ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-13 flex-col items-center justify-center gap-1 rounded-xl text-[.64rem] font-extrabold",
                active ? "bg-primary-soft text-primary" : "text-muted-foreground",
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);
  const isDark = mounted && resolvedTheme === "dark";

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={isDark ? "Use light theme" : "Use dark theme"}
      onClick={() => setTheme(isDark ? "light" : "dark")}
    >
      {isDark ? <Sun className="size-5" aria-hidden="true" /> : <Moon className="size-5" aria-hidden="true" />}
    </Button>
  );
}

function ConnectionIndicator() {
  const online = useSyncExternalStore(subscribeToNetwork, () => navigator.onLine, () => true);
  return (
    <span
      role="status"
      className={cn(
        "hidden items-center gap-2 rounded-full px-3 py-1.5 text-xs font-bold sm:flex",
        online ? "bg-success-soft text-success" : "bg-warning-soft text-warning",
      )}
    >
      <span aria-hidden="true" className="size-2 rounded-full bg-current" />
      {online ? "Online" : "Offline"}
    </span>
  );
}

function emptySubscribe() {
  return () => undefined;
}

function subscribeToNetwork(callback: () => void) {
  addEventListener("online", callback);
  addEventListener("offline", callback);
  return () => {
    removeEventListener("online", callback);
    removeEventListener("offline", callback);
  };
}

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
}
