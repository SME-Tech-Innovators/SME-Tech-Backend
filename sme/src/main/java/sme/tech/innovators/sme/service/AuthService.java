package sme.tech.innovators.sme.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.LoginRequest;
import sme.tech.innovators.sme.dto.response.AuthResponse;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.RefreshToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.AccountNotVerifiedException;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.TokenRevokedException;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Authenticates the user and issues a new JWT access token + refresh token.
     * Records the client IP and User-Agent for session tracking.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getAccountStatus() != AccountStatus.VERIFIED) {
            throw new AccountNotVerifiedException("Email verification required before login");
        }

        String accessToken = jwtService.generateAccessToken(user);

        String ip = extractIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("User {} logged in from IP={}", user.getEmail(), ip);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(900L)
                .build();
    }

    /**
     * Validates the refresh token and issues a new access token.
     * Updates lastUsedAt so you can see when the session was last active.
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            throw new TokenRevokedException("Refresh token has been revoked");
        }
        if (refreshToken.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        // Stamp last-used so we know the session is still active
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(900L)
                .build();
    }

    /**
     * Revokes the refresh token, effectively ending the session.
     * Records revokedAt so you can see exactly when the user logged out.
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token revoked for userId={}", refreshToken.getUserId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "unknown";
        // Truncate to match column length (512)
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }
}
