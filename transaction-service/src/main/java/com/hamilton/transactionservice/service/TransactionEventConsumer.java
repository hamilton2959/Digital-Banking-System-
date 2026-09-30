package com.hamilton.transactionservice.service;

import com.hamilton.transactionservice.entity.Transaction;
import com.hamilton.transactionservice.entity.TransactionStatus;
import com.hamilton.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";


    //Consume verification.required
    //Generate OTP and ask user to verify
    @KafkaListener(topics = "verification.required")
    public void consumerVerificationRequired(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required - transaction: {} reason: {}", transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                    () -> new RuntimeException("Transaction with id: " + transactionId + " not found"));

            if (transaction.getStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction: {} not PROCESSING - skipping", transactionId);
                return;
            }

            //Generate 6 Digit otp
            String otp = String.format("%6d", (int) (Math.random() * 900000) + 100000);

            //Store OTP in redis - expiry minutes = 5
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

            //Update Status
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP Generated for transaction: {} expires in {} minutes", transactionId, OTP_EXPIRY_MINUTES);

            //Notify User
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC, otpEvent);

        } catch (Exception e) {
            log.error("Exception handling verification required: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumerFraudCheckCleanResult(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);
        } catch (Exception e) {
            log.error("Error processing fraud check result: {}", e.getMessage());
        }
    }
}
