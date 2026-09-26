package com.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service entry point.
 * @EnableRetry activates Spring Retry for @Retryable on TransferService.
 * @EnableScheduling activates @Scheduled cleanup in IdempotencyService.
 */
@SpringBootApplication
@EnableRetry
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
