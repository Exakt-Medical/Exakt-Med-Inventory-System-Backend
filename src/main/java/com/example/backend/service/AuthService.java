package com.example.backend.service;

import com.example.backend.dto.auth.LoginRequestDto;
import com.example.backend.dto.auth.LoginResponseDto;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public LoginResponseDto login(LoginRequestDto request) {
        // 1. Ask Spring Security to authenticate (compares password with BCrypt)
        // If wrong username/password, this automatically throws BadCredentialsException
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. Fetch the user from the database
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. Generate a signed JWT token
        String token = jwtService.generateToken(user.getUsername());

        // 4. Return the token and user details
        return new LoginResponseDto(
                token,
                user.getName(),
                user.getUsername(),
                user.getRole().name(),
                "Login successful"
        );
    }

}
