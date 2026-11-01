package com.finsecure.pay.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record ValidationResult(String transactionId, String status, List<String> errors) {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @JsonIgnore
    public boolean isApproved() {
        return APPROVED.equals(status);
    }
}
