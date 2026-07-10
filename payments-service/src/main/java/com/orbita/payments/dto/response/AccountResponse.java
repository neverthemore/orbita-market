package com.orbita.payments.dto.response;

import com.orbita.payments.domain.Account;
import java.util.UUID;

public record AccountResponse(
        UUID accountId,
        String userId,
        Long balance,
        String currency
) {
    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUserId(),
                account.getBalance(),
                "geocredits"
        );
    }
}
