package com.smartstay.auth.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstay.auth.dto.AssignRoleRequest;
import com.smartstay.auth.dto.UserSummaryResponse;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.exceptions.ForbiddenException;
import com.smartstay.auth.exceptions.ResourceNotFoundException;
import com.smartstay.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    public Page<UserSummaryResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                             .map(u -> new UserSummaryResponse(u.getId(), u.getEmail(), u.getFirstName(),
                                     u.getLastName(), u.getRole(), u.isActive(), u.getCreatedAt()));
    }

    public UserSummaryResponse assignRole(UUID userId,
                                          AssignRoleRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ForbiddenException("Cannot assign the ADMIN role via this endpoint");
        }

        final var user = userRepository.findById(userId)
                                       .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Cannot change role of an ADMIN user");
        }

        log.info("Admin assigning role {} to user id={}", request.role(), userId);
        user.setRole(request.role());
        userRepository.save(user);

        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
