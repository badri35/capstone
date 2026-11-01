package com.finsecure.pay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionValidatorTest {

    private final TransactionValidator validator = new TransactionValidator();

    private TransactionRequest tx(String amount, String currency) {
        return new TransactionRequest("TX-100001", "ACC10000001", "ACC20000002",
                amount == null ? null : new BigDecimal(amount), currency);
    }

    @Test
    @DisplayName("valid transaction is approved")
    void validTransactionIsApproved() {
        ValidationResult result = validator.validate(tx("250.50", "USD"));
        assertThat(result.status()).isEqualTo(ValidationResult.APPROVED);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void amountAtLimitIsApproved() {
        assertThat(validator.validate(tx("10000.00", "EUR")).isApproved()).isTrue();
    }

    @Test
    void amountAboveLimitIsRejected() {
        ValidationResult result = validator.validate(tx("10000.01", "USD"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("per-transaction limit"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "-0.01"})
    void nonPositiveAmountIsRejected(String amount) {
        ValidationResult result = validator.validate(tx(amount, "USD"));
        assertThat(result.errors()).contains("amount must be greater than zero");
    }

    @Test
    void tooManyDecimalPlacesIsRejected() {
        ValidationResult result = validator.validate(tx("10.123", "USD"));
        assertThat(result.errors()).contains("amount must have at most 2 decimal places");
    }

    @Test
    void missingAmountIsRejected() {
        assertThat(validator.validate(tx(null, "USD")).errors()).contains("amount is required");
    }

    @Test
    void unsupportedCurrencyIsRejected() {
        ValidationResult result = validator.validate(tx("10.00", "XYZ"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.startsWith("currency must be one of"));
    }

    @Test
    void sameSourceAndDestinationIsRejected() {
        TransactionRequest request = new TransactionRequest("TX-100002", "ACC10000001", "ACC10000001",
                new BigDecimal("10.00"), "USD");
        assertThat(validator.validate(request).errors())
                .contains("sourceAccount and destinationAccount must differ");
    }

    @Test
    void malformedAccountIsRejected() {
        TransactionRequest request = new TransactionRequest("TX-100003", "bad", "ACC20000002",
                new BigDecimal("10.00"), "USD");
        assertThat(validator.validate(request).errors())
                .contains("sourceAccount must be 8-20 uppercase letters or digits");
    }

    @Test
    void malformedTransactionIdIsRejected() {
        TransactionRequest request = new TransactionRequest("x", "ACC10000001", "ACC20000002",
                new BigDecimal("10.00"), "USD");
        assertThat(validator.validate(request).isApproved()).isFalse();
    }

    @Test
    void nullRequestIsRejected() {
        assertThat(validator.validate(null).status()).isEqualTo(ValidationResult.REJECTED);
    }

    @Test
    void multipleErrorsAreAllReported() {
        TransactionRequest request = new TransactionRequest(null, null, null, null, null);
        assertThat(validator.validate(request).errors()).hasSize(5);
    }
}
