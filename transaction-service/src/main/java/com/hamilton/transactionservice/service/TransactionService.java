package com.hamilton.transactionservice.service;

import com.hamilton.transactionservice.client.AccountServiceClient;
import com.hamilton.transactionservice.dto.TransactionResponse;
import com.hamilton.transactionservice.dto.TransferRequest;
import com.hamilton.transactionservice.entity.Transaction;
import com.hamilton.transactionservice.entity.TransactionStatus;
import com.hamilton.transactionservice.entity.TransactionType;
import com.hamilton.transactionservice.event.TransactionInitiatedEvent;
import com.hamilton.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final AccountServiceClient accountServiceClient;
    private final TransactionRepository transactionRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";

    public TransactionResponse transfer(TransferRequest request){
        log.info("SAGA START - Transfer: {} -> {} Amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        //SAGA STEP 1 : Deduct from sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        //Save transaction as Processing
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        //SAGA STEP 3 : Publish for Fraud Check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found" + transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}
