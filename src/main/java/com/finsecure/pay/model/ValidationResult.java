package com.finsecure.pay.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record ValidationResult(String transactionId, String status, List<String> errors) {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    @Override
    public List<String> errors() {
        return List.copyOf(errors);
    }

    @JsonIgnore
    public boolean isApproved() {
        return APPROVED.equals(status);
    }
}
