package com.example.backend.config;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initSuperAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createUserIfNotExists(
                    userRepository,
                    passwordEncoder,
                    "superadmin",
                    "superadmin123",
                    "Super Administrator",
                    "superadmin@exaktmed.com",
                    "09123456789",
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
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setName(name);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password)); // BCrypt hash
            user.setEmail(email);
            user.setPhone(phone);
            user.setRole(role);
            user.setStatus(true);

            userRepository.save(user);
            System.out.println(">>> Initialized " + role + " user: username='" + username + "', password='" + password + "'");
        }
    }
}
