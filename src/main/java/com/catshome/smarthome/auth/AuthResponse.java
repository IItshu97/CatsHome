package com.catshome.smarthome.auth;

public record AuthResponse(
        String token,
        long expiresInMs
) {}