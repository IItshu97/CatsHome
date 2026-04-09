package com.catshome.smarthome.auth;

import com.catshome.smarthome.config.JwtProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthenticationManager authManager,
                          UserDetailsService userDetailsService,
                          JwtService jwtService,
                          JwtProperties jwtProperties) {
        this.authManager = authManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Authenticate and receive a JWT token.
     *
     * @return {@code 200 OK} with {@link AuthResponse} containing the Bearer token,
     *         or {@code 401 Unauthorized} on bad credentials.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));

        UserDetails user = userDetailsService.loadUserByUsername(req.username());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, jwtProperties.expirationMs()));
    }
}
