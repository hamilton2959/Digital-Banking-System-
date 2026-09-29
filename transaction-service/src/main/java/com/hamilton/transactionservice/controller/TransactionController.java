package com.hamilton.transactionservice.controller;

import com.hamilton.transactionservice.dto.TransactionResponse;
import com.hamilton.transactionservice.dto.TransferRequest;
import com.hamilton.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@RequestBody @Valid TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId){
        return ResponseEntity.ok(transactionService.getTransaction(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(@PathVariable String accountNumber){
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }
}
