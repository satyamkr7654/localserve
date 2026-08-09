import type { Metadata, Viewport } from "next";
import { AppProviders } from "@localserve/app-core";
import { shellIdentity } from "@localserve/app-security/session";
import { RoleShell, type NavItem } from "@localserve/ui";
import "./globals.css";

export const metadata: Metadata = { title: { default: "Operations console", template: "%s · LocalServe Admin" }, description: "Restricted LocalServe operations console.", robots: { index: false, follow: false } };
export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#0d1514" };
const navigation: NavItem[] = [{ href: "/", label: "Overview", icon: "dashboard" }, { href: "/providers", label: "Providers", icon: "users" }, { href: "/bookings", label: "Bookings", icon: "bookings" }, { href: "/finance", label: "Finance", icon: "finance" }, { href: "/disputes", label: "Disputes", icon: "disputes" }, { href: "/settings", label: "Settings", icon: "settings" }];
export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { const identity = await shellIdentity(); return <html lang="en" suppressHydrationWarning><body><AppProviders><RoleShell role="Admin" person={identity.person} initials={identity.initials} navigation={navigation}>{children}</RoleShell></AppProviders></body></html>; }
