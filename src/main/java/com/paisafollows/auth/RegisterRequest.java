package com.paisafollows.auth;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The JSON body of POST /api/auth/register. Each annotation is a rule checked before our code runs. */
public record RegisterRequest(

        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters")
        String password,

        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
        String phone,

        @NotNull @Past
        LocalDate dateOfBirth,

        @NotBlank @Pattern(regexp = "^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "must look like ABCDE1234F")
        String pan
) {
}   