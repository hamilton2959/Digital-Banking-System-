package com.hamilton.paymentservice.service;

import com.hamilton.paymentservice.dto.CreatePaymentRequest;
import com.hamilton.paymentservice.dto.PaymentOrderResponse;
import com.hamilton.paymentservice.entity.Payment;
import com.hamilton.paymentservice.entity.PaymentStatus;
import com.hamilton.paymentservice.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    //FLOW
    //1. Create PaymentIntent in Stripe
    //2. Save payment record in DB
    //3. Return payment details to frontend
    //4. Frontend presents Stripe Checkout / Elements
    //5. User pays
    //6. Stripe calls webhook
    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest request) throws StripeException {
        log.info("Creating payment Order for account: {} amount: {}",
                request.getAccountNumber(), request.getAmount());

        Stripe.apiKey = secretKey;

        // Converted Amount (cents)
        long convertedAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(convertedAmount)
                .setCurrency("usd")
                .setDescription(request.getDescription())
                .putMetadata("accountNumber", request.getAccountNumber())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        log.info("Stripe PaymentIntent Created: {}", paymentIntent.getId());

        // Save payment record
        Payment payment = new Payment();
        // Preserving logic: Storing Stripe PaymentIntent ID in the order field
        payment.setRazorpayOrderId(paymentIntent.getId());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());

        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                savedPayment.getId(),
                paymentIntent.getId(),
                request.getAmount(),
                "USD",
                "CREATED",
                publishableKey
        );
    }

    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received Stripe webhook event: {}", payload.get("type"));

        String event = (String) payload.get("type");

        if ("payment_intent.succeeded".equals(event)) {
            handlePaymentSuccess(payload);
        } else if ("payment_intent.payment_failed".equals(event)) {
            handlePaymentFailure(payload);
        }
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("id"); // Stripe PaymentIntent ID
            String paymentId = (String) paymentData.get("id"); // In Stripe, PaymentIntent ID represents the payment session

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment Not Found for order: " + orderId));

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // Publish payment completed event
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, payment.getId(), event);
            log.info("Payment Completed: {}", payment.getId());
        } catch (Exception e) {
            log.error("Error handling payment success: {}", e.getMessage());
        }
    }

    private void handlePaymentFailure(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment Not Found for order: " + orderId));

            // Retaining original logic: updates to COMPLETED status on failure
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setFailureReason("Payment failed via Stripe");
            paymentRepository.save(payment);

            // Publish payment failed event
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", "Payment failed via Stripe");

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, payment.getId(), event);

            log.warn("Payment Failed: {}", payment.getId());

        } catch (Exception e) {
            log.error("Error handling payment failure: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        return (Map<String, Object>) data.get("object");
    }
}