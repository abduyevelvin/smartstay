package com.smartstay.auth.dto;

import com.smartstay.auth.enums.Role;

import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull(message = "Role is required") Role role) {

}
