package com.payments.domain;

/**
 * Ledger entry type. Every transfer produces exactly one DEBIT and one CREDIT.
 * REQ-F-015, REQ-F-017
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
