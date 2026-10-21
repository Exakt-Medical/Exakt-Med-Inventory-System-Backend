package com.example.backend.controller;

import com.example.backend.dto.user.UserRequestDto;
import com.example.backend.dto.user.UserResponseDto;
import com.example.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User Management", description = "Endpoints for Super Admin user provisioning, updates, and role management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 1. CREATE USER
    @PostMapping
    @Operation(summary = "Create User", description = "Creates a new system user with credentials, email, and assigned role (SuperAdmin, Admin, Pharmacist, Procurement).")
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto request) {
        UserResponseDto response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL USERS
    @GetMapping
    @Operation(summary = "Get All Users", description = "Retrieves a list of all users in the system.")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 3. UPDATE USER
    @PutMapping("/{id}")
    @Operation(summary = "Update User", description = "Updates user username, email, active status, or role.")
    public ResponseEntity<UserResponseDto> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDto request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    // 4. DELETE USER
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete User", description = "Removes a user account from the system.")
    public ResponseEntity<String> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }
}
