package com.hamilton.transactionservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionInitiatedEvent {
    private String transactionId;
    private String senderAccountNumber;
    private String recipientAccountNumber;
    private BigDecimal amount;
    private String description;
}
