package com.example.backend.config;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.bootstrap.superadmin.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.superadmin.username:superadmin}")
    private String superAdminUsername;

    @Value("${app.bootstrap.superadmin.password:}")
    private String superAdminPassword;

    @Value("${app.bootstrap.superadmin.name:Super Administrator}")
    private String superAdminName;

    @Value("${app.bootstrap.superadmin.email:superadmin@exaktmed.com}")
    private String superAdminEmail;

    @Value("${app.bootstrap.superadmin.phone:09123456789}")
    private String superAdminPhone;

    @Bean
    public CommandLineRunner initSuperAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!bootstrapEnabled) {
                return;
            }
            createUserIfNotExists(
                    userRepository,
                    passwordEncoder,
                    superAdminUsername,
                    superAdminPassword,
                    superAdminName,
                    superAdminEmail,
                    superAdminPhone,
                    User.Role.SuperAdmin
            );
        };
    }

    private void createUserIfNotExists(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       String username,
                                       String password,
                                       String name,
                                       String email,
                                       String phone,
                                       User.Role role) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (userRepository.findByUsername(username).isEmpty()) {
            if (password == null || password.isBlank()) {
                log.warn("Skipping initial {} user creation for '{}' because SUPERADMIN_PASSWORD is not configured.", role, username);
                return;
            }

            User user = new User();
            user.setName(name);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password)); // BCrypt hash
            user.setEmail(email);
            user.setPhone(phone);
            user.setRole(role);
            user.setStatus(true);

            userRepository.save(user);
            log.info("Initialized {} user: username='{}'", role, username);
        }
    }
}
