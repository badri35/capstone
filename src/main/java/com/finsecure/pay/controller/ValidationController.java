package com.finsecure.pay.controller;

import com.finsecure.pay.model.TransactionRequest;
import com.finsecure.pay.model.ValidationResult;
import com.finsecure.pay.service.TransactionValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationController {

    private static final Logger LOG = LoggerFactory.getLogger(ValidationController.class);

    private final TransactionValidator validator;
    private final Counter approved;
    private final Counter rejected;
    private final String appVersion;

    public ValidationController(TransactionValidator validator, MeterRegistry registry,
                                @Value("${app.version:dev}") String appVersion) {
        this.validator = validator;
        this.appVersion = appVersion;
        this.approved = Counter.builder("finsecure_transactions_total").tag("result", "approved")
                .description("Transactions validated").register(registry);
        this.rejected = Counter.builder("finsecure_transactions_total").tag("result", "rejected")
                .description("Transactions validated").register(registry);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "version", appVersion);
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody TransactionRequest request) {
        ValidationResult result = validator.validate(request);
        if (result.isApproved()) {
            approved.increment();
            LOG.info("transaction approved id={}", result.transactionId());
            return ResponseEntity.ok(result);
        }
        rejected.increment();
        LOG.warn("transaction rejected id={} errors={}", result.transactionId(), result.errors());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }
}
