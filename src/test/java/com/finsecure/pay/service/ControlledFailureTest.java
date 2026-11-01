package com.finsecure.pay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlledFailureTest {

    @Test
    void deliberatelyFailingTest() {
        assertEquals(1, 2, "controlled failure to prove the pipeline blocks deployment");
    }
}
