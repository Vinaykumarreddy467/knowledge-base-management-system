package com.kbms.security;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.user.AppUser;
import com.kbms.user.Role;
import com.kbms.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthController(
            UserRepository users,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuditService audit) {
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var email = request.email().trim().toLowerCase();
        // Authentication already verified the credentials; load the domain user to
        // sign the token and to build the response.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Invalid email or password"));
        audit.record(user, "AUTH_LOGIN", "USER", String.valueOf(user.getId()), null);
        return new LoginResponse(jwtService.issue(user), jwtService.expiresInSeconds(), UserResponse.of(user));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.of(CurrentUser.require());
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 200) String password) {}

    public record LoginResponse(String accessToken, long expiresInSeconds, UserResponse user) {}

    public record UserResponse(Long id, String email, String fullName, Role role, boolean active, Instant createdAt) {
        static UserResponse of(AppUser user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(), user.getCreatedAt());
        }
    }
}
