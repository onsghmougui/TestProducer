package com.example.producerback.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userIdentityNumber;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private String sender;

    private String receiver;

    private Double debit;

    private Double credit;

    private String description;
    private String reference;

    private Boolean pushed; //we will see if the transaction is pushed to the broker or no
    private String logs;
    private LocalDateTime timestamp;

}