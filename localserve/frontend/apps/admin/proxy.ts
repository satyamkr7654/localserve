import type { NextRequest } from "next/server";
import { proxy as applySecurity } from "../../packages/app-security/src/proxy";
export function proxy(request: NextRequest) { return applySecurity(request); }
export const config = { matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"] };
