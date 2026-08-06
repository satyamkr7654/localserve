import type { Metadata, Viewport } from "next";
import { AppProviders } from "@localserve/app-core";
import { RoleShell, type NavItem } from "@localserve/ui";
import "./globals.css";

export const metadata: Metadata = { title: { default: "Provider workspace", template: "%s · LocalServe Provider" }, description: "Manage LocalServe jobs, availability and earnings.", applicationName: "LocalServe Provider", manifest: "/manifest.webmanifest" };
export const viewport: Viewport = { width: "device-width", initialScale: 1, viewportFit: "cover", themeColor: "#087669" };
const navigation: NavItem[] = [{ href: "/", label: "Overview", icon: "home" }, { href: "/jobs", label: "Jobs", icon: "jobs" }, { href: "/earnings", label: "Earnings", icon: "earnings" }, { href: "/schedule", label: "Schedule", icon: "calendar" }, { href: "/profile", label: "Profile", icon: "profile" }];
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en" suppressHydrationWarning><body><AppProviders><RoleShell role="Provider" person="Ravi Kumar" initials="RK" navigation={navigation}>{children}</RoleShell></AppProviders></body></html>; }
