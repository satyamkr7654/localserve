import { PasswordResetPage } from "@localserve/ui";
export default async function Page({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token } = await searchParams; return <PasswordResetPage token={token ?? ""} />; }
