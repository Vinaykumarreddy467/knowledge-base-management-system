package com.kbms.config;

import com.kbms.common.ApiException;
import com.kbms.user.AppUser;
import com.kbms.user.Role;
import com.kbms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email.trim().toLowerCase())
                .filter(AppUser::isActive)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No active user for the supplied email"));
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /**
     * First admin exists only when both KBMS_ADMIN_EMAIL and KBMS_ADMIN_PASSWORD are supplied.
     * There is no built-in default credential to leak or forget to rotate.
     */
    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users, PasswordEncoder encoder, AppProperties properties) {
        return args -> {
            var admin = properties.bootstrapAdmin();
            boolean configured = admin.email() != null
                    && !admin.email().isBlank()
                    && admin.password() != null
                    && !admin.password().isBlank();
            if (!configured) {
                log.warn("No bootstrap admin configured. Set KBMS_ADMIN_EMAIL and KBMS_ADMIN_PASSWORD "
                        + "to create the first administrator, or the system starts with no users.");
                return;
            }
            if (users.count() > 0) {
                return;
            }
            if (admin.password().length() < 12) {
                throw new ApiException(400, "WEAK_BOOTSTRAP_PASSWORD",
                        "KBMS_ADMIN_PASSWORD must be at least 12 characters");
            }
            users.save(new AppUser(
                    admin.email().trim().toLowerCase(),
                    encoder.encode(admin.password()),
                    "Bootstrap Administrator",
                    Role.ADMIN));
            log.info("Created bootstrap administrator {}", admin.email());
        };
    }
}
