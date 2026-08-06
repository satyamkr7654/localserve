import type { Money } from "@localserve/contracts";

export function formatMoney(money: Money, locale = "en-IN") {
  return new Intl.NumberFormat(locale, { style: "currency", currency: money.currency, maximumFractionDigits: 0 })
    .format(money.amountMinor / 100);
}

export function formatDateTime(value: string, locale = "en-IN") {
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
