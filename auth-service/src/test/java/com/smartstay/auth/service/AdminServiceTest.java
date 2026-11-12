package com.smartstay.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.smartstay.auth.dto.AssignRoleRequest;
import com.smartstay.auth.dto.UserSummaryResponse;
import com.smartstay.auth.entity.UserEntity;
import com.smartstay.auth.enums.Role;
import com.smartstay.auth.exceptions.ForbiddenException;
import com.smartstay.auth.exceptions.ResourceNotFoundException;
import com.smartstay.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminService adminService;

    private UserEntity guestUser;
    private UserEntity managerUser;
    private UserEntity adminUser;

    @BeforeEach
    void setup() {
        guestUser = makeUser(Role.GUEST, "guest@smartstay.com");
        managerUser = makeUser(Role.HOTEL_MANAGER, "manager@smartstay.com");
        adminUser = makeUser(Role.ADMIN, "admin@smartstay.com");
    }

    // ─────────────────────────────────────────────────────────────
    // getAllUsers
    // ─────────────────────────────────────────────────────────────

    @Test
    void getAllUsers_shouldReturnPageOfSummaries() {
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(guestUser, managerUser)));

        var result = adminService.getAllUsers(pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()
                         .get(0)
                         .email()).isEqualTo("guest@smartstay.com");
        assertThat(result.getContent()
                         .get(1)
                         .role()).isEqualTo(Role.HOTEL_MANAGER);
    }

    @Test
    void getAllUsers_shouldMapAllFieldsCorrectly() {
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(guestUser)));

        UserSummaryResponse summary = adminService.getAllUsers(pageable)
                                                  .getContent()
                                                  .get(0);

        assertThat(summary.id()).isEqualTo(guestUser.getId());
        assertThat(summary.email()).isEqualTo(guestUser.getEmail());
        assertThat(summary.firstName()).isEqualTo(guestUser.getFirstName());
        assertThat(summary.lastName()).isEqualTo(guestUser.getLastName());
        assertThat(summary.role()).isEqualTo(Role.GUEST);
        assertThat(summary.active()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────
    // assignRole — happy paths
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignRole_shouldPromoteGuestToHotelManager() {
        when(userRepository.findById(guestUser.getId())).thenReturn(Optional.of(guestUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSummaryResponse result = adminService.assignRole(guestUser.getId(),
                new AssignRoleRequest(Role.HOTEL_MANAGER));

        assertThat(result.role()).isEqualTo(Role.HOTEL_MANAGER);
        verify(userRepository).save(guestUser);
    }

    @Test
    void assignRole_shouldDemoteHotelManagerToGuest() {
        when(userRepository.findById(managerUser.getId())).thenReturn(Optional.of(managerUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSummaryResponse result = adminService.assignRole(managerUser.getId(), new AssignRoleRequest(Role.GUEST));

        assertThat(result.role()).isEqualTo(Role.GUEST);
        verify(userRepository).save(managerUser);
    }

    // ─────────────────────────────────────────────────────────────
    // assignRole — guard: cannot assign ADMIN role
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignRole_shouldThrowWhenTargetRoleIsAdmin() {
        assertThatThrownBy(
                () -> adminService.assignRole(guestUser.getId(), new AssignRoleRequest(Role.ADMIN))).isInstanceOf(
                                                                                                            ForbiddenException.class)
                                                                                                    .hasMessageContaining(
                                                                                                            "ADMIN");

        // Guard fires before any DB lookup
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────
    // assignRole — guard: cannot change an ADMIN user's role
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignRole_shouldThrowWhenTargetUserIsAdmin() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(
                () -> adminService.assignRole(adminUser.getId(), new AssignRoleRequest(Role.GUEST))).isInstanceOf(
                                                                                                            ForbiddenException.class)
                                                                                                    .hasMessageContaining(
                                                                                                            "ADMIN");

        verify(userRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────
    // assignRole — guard: user not found
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignRole_shouldThrowWhenUserNotFound() {
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> adminService.assignRole(randomId, new AssignRoleRequest(Role.HOTEL_MANAGER))).isInstanceOf(
                                                                                                           ResourceNotFoundException.class)
                                                                                                   .hasMessageContaining(
                                                                                                           randomId.toString());

        verify(userRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private static UserEntity makeUser(Role role,
                                       String email) {
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setFirstName("First");
        u.setLastName("Last");
        u.setPassword("$2a$hashed");
        u.setRole(role);
        u.setActive(true);
        return u;
    }
}
