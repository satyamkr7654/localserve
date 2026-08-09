import { cookies } from "next/headers";

export async function shellIdentity() {
  const jar = await cookies();
  const token = jar.get("__Host-localserve_access")?.value ?? jar.get("localserve_access")?.value;
  if (!token) return { person: "LocalServe user", initials: "LS" };
  try {
    const encoded = token.split(".")[1];
    if (!encoded) throw new Error("invalid token");
    const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8")) as { display_name?: string };
    const person = payload.display_name?.trim() || "LocalServe user";
    const initials = person.split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "LS";
    return { person, initials };
  } catch {
    return { person: "LocalServe user", initials: "LS" };
  }
}
