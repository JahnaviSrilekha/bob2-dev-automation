package com.payments.exception;

/** Thrown when senderAccountId equals receiverAccountId (REQ-F-004). */
public class SelfTransferException extends RuntimeException {
    public SelfTransferException() {
        super("Sender and receiver account must be different");
    }
}
