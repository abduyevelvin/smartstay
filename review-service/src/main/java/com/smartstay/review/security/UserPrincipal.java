package com.smartstay.review.security;

import java.util.UUID;

public record UserPrincipal(UUID userId, String email, String name) {

}
