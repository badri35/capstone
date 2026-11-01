package com.finsecure.pay.service;

import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TransactionValidator {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");
    static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR", "SGD");
    private static final Pattern ACCOUNT = Pattern.compile("^[A-Z0-9]{8,20}$");
    private static final Pattern TX_ID = Pattern.compile("^[A-Za-z0-9-]{6,64}$");

    public ValidationResult validate(TransactionRequest tx) {
        List<String> errors = new ArrayList<>();

        if (tx == null) {
            return new ValidationResult(null, ValidationResult.REJECTED, List.of("Request body is required"));
        }

        if (tx.transactionId() == null || !TX_ID.matcher(tx.transactionId()).matches()) {
            errors.add("transactionId must be 6-64 characters (letters, digits, hyphen)");
        }

        boolean sourceOk = validAccount(tx.sourceAccount(), "sourceAccount", errors);
        boolean destOk = validAccount(tx.destinationAccount(), "destinationAccount", errors);
        if (sourceOk && destOk && tx.sourceAccount().equals(tx.destinationAccount())) {
            errors.add("sourceAccount and destinationAccount must differ");
        }

        validateAmount(tx.amount(), errors);

        if (tx.currency() == null || !SUPPORTED_CURRENCIES.contains(tx.currency())) {
            errors.add("currency must be one of " + String.join(",", new java.util.TreeSet<>(SUPPORTED_CURRENCIES)));
        }

        String status = errors.isEmpty() ? ValidationResult.APPROVED : ValidationResult.REJECTED;
        return new ValidationResult(tx.transactionId(), status, List.copyOf(errors));
    }

    private boolean validAccount(String value, String field, List<String> errors) {
        if (value == null || !ACCOUNT.matcher(value).matches()) {
            errors.add(field + " must be 8-20 uppercase letters or digits");
            return false;
        }
        return true;
    }

    private void validateAmount(BigDecimal amount, List<String> errors) {
        if (amount == null) {
            errors.add("amount is required");
            return;
        }
        if (amount.signum() <= 0) {
            errors.add("amount must be greater than zero");
        } else if (amount.compareTo(MAX_AMOUNT) > 0) {
            errors.add("amount exceeds the per-transaction limit of " + MAX_AMOUNT.toPlainString());
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            errors.add("amount must have at most 2 decimal places");
        }
    }
}
