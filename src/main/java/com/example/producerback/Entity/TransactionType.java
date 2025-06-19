package com.example.producerback.Entity;

public enum TransactionType {
    VIREMENT(9),
    PAIEMENT_PAR_CARTE(7),
    CREDIT(5),
    PAIEMENT_FACTURE(3);

    private final int priority;

    TransactionType(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}