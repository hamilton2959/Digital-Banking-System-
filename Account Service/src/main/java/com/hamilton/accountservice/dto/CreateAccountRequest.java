package com.hamilton.accountservice.dto;

import com.hamilton.accountservice.entity.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAccountRequest {
    @NotBlank(message = "Account Holder name is Required")
    private String accountHolderName;

    @NotBlank(message = "Email is Required")
    @Email(message = "Invalid Email format")
    private String email;

    @NotBlank(message = "Phone Number is Required")
    private String phone;

    @NotNull(message = "Account Type is Required")
    private AccountType accountType;

    @NotNull(message = "Initial Deposit is Required")
    @Positive(message = "Initial Deposit must be positive")
    private BigDecimal initialDeposit;
}
