import { EmailVerificationPage } from "@localserve/ui";
export default async function Page({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token } = await searchParams; return <EmailVerificationPage token={token ?? ""} />; }
