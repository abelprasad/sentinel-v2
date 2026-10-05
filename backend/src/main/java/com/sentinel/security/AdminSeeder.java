package com.sentinel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the initial admin user from {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD}
 * on first boot. Idempotent — skips when the user already exists.
 *
 * <p>There is deliberately no registration endpoint. v1's open
 * {@code /auth/register} let anyone self-register as ADMIN; that hole
 * does not come back.
 */
@Component
@Order(1)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment env;

    public AdminSeeder(AppUserRepository users, PasswordEncoder encoder, Environment env) {
        this.users = users;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = env.getProperty("ADMIN_USERNAME");
        String password = env.getProperty("ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("ADMIN_USERNAME/ADMIN_PASSWORD not set — no admin user seeded; admin API will be unreachable");
            return;
        }
        if (users.existsByUsername(username)) {
            log.info("Admin user '{}' already exists, skipping seed", username);
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Seeded initial admin user '{}'", username);
    }
}
