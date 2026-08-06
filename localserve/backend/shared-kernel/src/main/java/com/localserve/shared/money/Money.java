package com.localserve.shared.money;

import java.util.Currency;
import java.util.Objects;

public record Money(long amountMinor, Currency currency) implements Comparable<Money> {
    private static final int BASIS_POINT_DENOMINATOR = 10_000;

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amountMinor, String currencyCode) {
        return new Money(amountMinor, Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of(0, currencyCode);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    /** Returns a deterministic floor-rounded portion; callers assign the remainder explicitly. */
    public Money portionBasisPoints(int basisPoints) {
        if (basisPoints < 0 || basisPoints > BASIS_POINT_DENOMINATOR) {
            throw new IllegalArgumentException("basisPoints must be between 0 and 10000");
        }
        if (amountMinor < 0) {
            throw new IllegalStateException("basis-point portions require a nonnegative amount");
        }
        long product = Math.multiplyExact(amountMinor, basisPoints);
        return new Money(product / BASIS_POINT_DENOMINATOR, currency);
    }

    public Money requireNonNegative(String field) {
        if (amountMinor < 0) {
            throw new IllegalArgumentException(field + " must be nonnegative");
        }
        return this;
    }

    public Money requirePositive(String field) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return this;
    }

    public void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }
}
