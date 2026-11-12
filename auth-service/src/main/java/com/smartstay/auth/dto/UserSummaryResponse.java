package com.smartstay.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.smartstay.auth.enums.Role;

public record UserSummaryResponse(UUID id, String email, String firstName, String lastName, Role role, boolean active,
                                  OffsetDateTime createdAt) {

}
