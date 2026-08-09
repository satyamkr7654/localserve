import type { NextRequest } from "next/server";
import { proxy as applySecurity } from "@localserve/app-security/proxy";
export function proxy(request: NextRequest) { return applySecurity(request, "ADMIN"); }
export const config = { matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"] };
