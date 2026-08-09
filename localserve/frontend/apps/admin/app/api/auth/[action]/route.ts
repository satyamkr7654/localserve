import { handleAuth, type AuthAction } from "@localserve/app-security/bff";
import type { NextRequest } from "next/server";

const actions = new Set<AuthAction>(["login", "mfa", "refresh", "logout", "adopt"]);
export async function POST(request: NextRequest, context: { params: Promise<{ action: string }> }) {
  const { action } = await context.params;
  if (!actions.has(action as AuthAction)) return new Response(null, { status: 404 });
  return handleAuth(request, "ADMIN", action as AuthAction);
}
