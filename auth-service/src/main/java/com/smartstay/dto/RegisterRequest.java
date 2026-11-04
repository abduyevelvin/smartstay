package com.smartstay.dto;

import com.smartstay.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@Email @NotBlank String email,

                              @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,

                              @NotBlank String firstName,

                              @NotBlank String lastName,

                              @NotNull(message = "GUEST or HOTEL_MANAGER") Role role) {

}
