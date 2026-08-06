package com.localserve.finance.ledger;

import com.localserve.shared.money.Money;

import java.util.Objects;

public record LedgerLine(LedgerSide side, LedgerAccountType accountType, Money amount, String memoCode) {
    public LedgerLine {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(amount, "amount").requirePositive("ledger line amount");
        if (memoCode == null || !memoCode.matches("[A-Z][A-Z0-9_]{2,79}")) {
            throw new IllegalArgumentException("memoCode has an invalid format");
        }
    }

    public static LedgerLine debit(LedgerAccountType account, Money amount, String memoCode) {
        return new LedgerLine(LedgerSide.DEBIT, account, amount, memoCode);
    }

    public static LedgerLine credit(LedgerAccountType account, Money amount, String memoCode) {
        return new LedgerLine(LedgerSide.CREDIT, account, amount, memoCode);
    }
}
