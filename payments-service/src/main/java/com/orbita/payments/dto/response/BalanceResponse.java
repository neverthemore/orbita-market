package com.orbita.payments.dto.response;

import com.orbita.payments.domain.Account;

public record BalanceResponse(
        String userId,
        Long balance,
        String currency
) {
    public static BalanceResponse of(Account account) {
        return new BalanceResponse(
                account.getUserId(),
                account.getBalance(),
                "geocredits"
        );
    }
}
