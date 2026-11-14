package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.UserDTOs.UserProfileResponse;
import com.apg.promociones.apg_api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getAuthenticatedUserProfile());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(userService.updateAuthenticatedUserProfile(payload));
    }
}