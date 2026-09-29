package com.kbms.user;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration. Administrator only; there is no self-service registration endpoint. */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> list(int page, int size) {
        return PageResponse.of(
                users.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by("email"))), UserView::of);
    }

    @Transactional
    public UserView create(@Valid CreateRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A user with that email already exists");
        }
        AppUser saved = users.save(new AppUser(email, encoder.encode(request.password()), request.fullName().trim(), request.role()));
        audit.recordCurrentUser("USER_CREATE", "USER", String.valueOf(saved.getId()), email + " " + saved.getRole());
        return UserView.of(saved);
    }

    @Transactional
    public UserView update(long id, @Valid UpdateRequest request) {
        AppUser user = require(id);
        if (request.role() != null && request.role() != user.getRole()) {
            protectLastAdmin(user, request.role() != Role.ADMIN);
            user.setRole(request.role());
            audit.recordCurrentUser("USER_ROLE_CHANGE", "USER", String.valueOf(id), user.getRole().name());
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.active() != null && request.active() != user.isActive()) {
            protectLastAdmin(user, !request.active());
            user.setActive(request.active());
            audit.recordCurrentUser("USER_DEACTIVATE", "USER", String.valueOf(id), String.valueOf(user.isActive()));
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(encoder.encode(request.password()));
            audit.recordCurrentUser("USER_PASSWORD_RESET", "USER", String.valueOf(id), null);
        }
        user.setUpdatedAt(Instant.now());
        users.save(user);
        return UserView.of(user);
    }

    @Transactional
    public void delete(long id) {
        AppUser user = require(id);
        protectLastAdmin(user, true);
        users.delete(user);
        audit.recordCurrentUser("USER_DELETE", "USER", String.valueOf(id), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AppUser require(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User " + id + " was not found"));
    }

    /** The last active administrator must not be demoted, disabled or deleted. */
    private void protectLastAdmin(AppUser user, boolean removing) {
        if (removing && user.getRole() == Role.ADMIN && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("The last active administrator cannot be removed");
        }
    }

    private String normalise(String email) {
        return email.trim().toLowerCase();
    }

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotBlank @Size(max = 200) String fullName,
            @NotNull Role role) {}

    public record UpdateRequest(
            @Size(max = 200) String fullName,
            @Size(min = 12, max = 200) String password,
            Role role,
            Boolean active) {}

    public record UserView(Long id, String email, String fullName, Role role, boolean active, Instant createdAt) {
        public static UserView of(AppUser user) {
            return new UserView(
                    user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(), user.getCreatedAt());
        }
    }
}
