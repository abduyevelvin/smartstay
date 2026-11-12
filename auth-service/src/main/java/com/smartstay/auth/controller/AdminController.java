package com.smartstay.auth.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstay.auth.dto.AssignRoleRequest;
import com.smartstay.auth.dto.UserSummaryResponse;
import com.smartstay.auth.service.AdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryResponse>> getAllUsers(@PageableDefault(size = 20,
            sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllUsers(pageable));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserSummaryResponse> assignRole(@PathVariable UUID id,
                                                          @RequestBody @Valid AssignRoleRequest request) {
        return ResponseEntity.ok(adminService.assignRole(id, request));
    }
}
