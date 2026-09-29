package com.hamilton.transactionservice.repository;

import com.hamilton.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findBySenderAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
