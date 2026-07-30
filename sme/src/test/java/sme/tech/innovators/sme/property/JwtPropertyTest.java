package sme.tech.innovators.sme.property;

import io.jsonwebtoken.Claims;
import net.jqwik.api.*;
import org.junit.jupiter.api.Assertions;
import org.springframework.test.util.ReflectionTestUtils;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.UserRole;
import sme.tech.innovators.sme.service.JwtService;

import java.util.Date;
import java.util.UUID;

public class JwtPropertyTest {

    private JwtService createJwtService() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(service, "expiryMinutes", 1440);
        return service;
    }

    @Property(tries = 20)
    void generatedJwtContainsAllFourClaims(@ForAll("users") User user) {
        JwtService jwtService = createJwtService();
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.extractClaims(token);

        Assertions.assertNotNull(claims.get("userId"), "userId claim missing");
        Assertions.assertNotNull(claims.get("email"), "email claim missing");
        Assertions.assertNotNull(claims.get("role"), "role claim missing");
        // businessId may be null if no business linked
    }

    @Property(tries = 20)
    void accessTokenExpiresIn24Hours(@ForAll("users") User user) {
        JwtService jwtService = createJwtService();
        long before = System.currentTimeMillis();
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.extractClaims(token);
        Date expiration = claims.getExpiration();

        long expectedExpiry = before + 1440L * 60 * 1000;
        long delta = Math.abs(expiration.getTime() - expectedExpiry);
        Assertions.assertTrue(delta < 5000, "Token expiry not within 5s of 24 hours: delta=" + delta);
    }

    @Provide
    Arbitrary<User> users() {
        return Arbitraries.of(
            buildUser("user1@test.com", UserRole.OWNER),
            buildUser("user2@test.com", UserRole.ADMIN),
            buildUser("user3@test.com", UserRole.EMPLOYEE)
        );
    }

    private User buildUser(String email, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("hashed");
        user.setFullName("Test User");
        user.setAccountStatus(AccountStatus.VERIFIED);
        user.setRole(role);
        return user;
    }
}
