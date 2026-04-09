package com.catshome.smarthome.config;

import com.catshome.smarthome.entity.User;
import com.catshome.smarthome.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default admin user on first startup if no users exist.
 * Credentials are controlled by {@code DEFAULT_ADMIN_USERNAME} / {@code DEFAULT_ADMIN_PASSWORD} env vars.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin-username:admin}")
    private String defaultUsername;

    @Value("${app.default-admin-password:admin}")
    private String defaultPassword;

    public DataInitializer(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() == 0) {
            User admin = new User();
            admin.setUsername(defaultUsername);
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            userRepo.save(admin);
            log.info("Default admin user created (username: '{}'). Change credentials in production!", defaultUsername);
        }
    }
}