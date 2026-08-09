import type { Metadata, Viewport } from "next";
import { AppProviders } from "@localserve/app-core";
import { shellIdentity } from "@localserve/app-security/session";
import { RoleShell, type NavItem } from "@localserve/ui";
import "./globals.css";

export const metadata: Metadata = { title: { default: "LocalServe", template: "%s · LocalServe" }, description: "Book trusted local professionals near you.", applicationName: "LocalServe Customer", manifest: "/manifest.webmanifest" };
export const viewport: Viewport = { width: "device-width", initialScale: 1, viewportFit: "cover", themeColor: [{ media: "(prefers-color-scheme: light)", color: "#f6f8f7" }, { media: "(prefers-color-scheme: dark)", color: "#0d1514" }] };
const navigation: NavItem[] = [{ href: "/", label: "Home", icon: "home" }, { href: "/search", label: "Explore", icon: "search" }, { href: "/bookings", label: "Bookings", icon: "bookings" }, { href: "/account", label: "Account", icon: "profile" }];

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const identity = await shellIdentity();
  return <html lang="en" suppressHydrationWarning><body><AppProviders><RoleShell role="Customer" person={identity.person} initials={identity.initials} navigation={navigation}>{children}</RoleShell></AppProviders></body></html>;
}
