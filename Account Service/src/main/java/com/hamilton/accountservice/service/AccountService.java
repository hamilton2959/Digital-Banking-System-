package com.hamilton.accountservice.service;

import com.hamilton.accountservice.dto.AccountResponse;
import com.hamilton.accountservice.dto.CreateAccountRequest;
import com.hamilton.accountservice.entity.Account;
import com.hamilton.accountservice.entity.AccountStatus;
import com.hamilton.accountservice.entity.AccountType;
import com.hamilton.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Create account with request: {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account with email " + request.getEmail() + " already exists");
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS ? new BigDecimal("100000") : new BigDecimal("500000")
        );

        Account savedAccount = accountRepository.save(account);
        log.info("Account created with number {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    //Generating a 12 digit unique account number
    private String generateAccountNumber() {
        String accountNumber;

        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%12d", number);

        }while (accountRepository.existsByAccountNumber(accountNumber));
        
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number " + accountNumber + " not found"));

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number " + accountNumber + " not found"));

        return account.getBalance();
    }

    // Block Account = called by fraud detection service via Kafka
    public void blockAccount(String accountNumber) {
        log.info("Blocking account number {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number " + accountNumber + " not found"));

        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked with number {}", accountNumber);
    }

    //Deduct Balance from sender account
    //called by transaction service
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance {} from account {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number " + accountNumber + " not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account status " + account.getStatus() + " is not active");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Account balance " + amount + " is below 0");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated: {}", account.getBalance());
    }

    //Credit balance
    //called by transaction service via Kafka
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number " + accountNumber + " not found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance credit: New Balance is {}", account.getBalance());
    }
}
