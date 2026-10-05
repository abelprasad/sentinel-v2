package com.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinel.ingestion.AdsbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Auth slice: login issues tokens, bad credentials don't leak which half was
 * wrong, admin endpoints enforce roles, and there is no registration.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtTokenService tokens;

    @Autowired
    private LoginRateLimiter rateLimiter;

    /**
     * The ingestion scheduler runs on its own thread outside the test
     * transaction — mock the client so it never hits the real ADS-B API.
     */
    @MockBean
    private AdsbClient adsbClient;

    @BeforeEach
    void seedUsers() {
        rateLimiter.clear();
        createUser("admin", "admin-pass", Role.ADMIN);
        createUser("analyst", "analyst-pass", Role.ANALYST);
        createUser("operator", "operator-pass", Role.OPERATOR);
    }

    private void createUser(String username, String password, Role role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        users.save(user);
    }

    private String tokenFor(String username, Role role) {
        return "Bearer " + tokens.generate(username, role);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        String token = result.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        assertThat(token).isNotBlank();
        return "Bearer " + token;
    }

    @Test
    void loginIssuesUsableToken() throws Exception {
        String bearer = login("admin", "admin-pass");

        mockMvc.perform(get("/api/admin/aircraft").header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void unknownUserIsIndistinguishable401() throws Exception {
        // Same status and error code as wrong password — no user enumeration.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void adminEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/admin/aircraft")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointWithGarbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/admin/aircraft").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analystCanReadButNotMutate() throws Exception {
        String analyst = tokenFor("analyst", Role.ANALYST);

        mockMvc.perform(get("/api/admin/aircraft").header("Authorization", analyst))
                .andExpect(status().isOk());
        mockMvc.perform(
                        delete(
                                        "/api/admin/aircraft/1")
                                .header("Authorization", analyst))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanMutate() throws Exception {
        String operator = tokenFor("operator", Role.OPERATOR);

        // 404 (no such aircraft), not 403 — the role check passed.
        mockMvc.perform(
                        delete(
                                        "/api/admin/aircraft/999999")
                                .header("Authorization", operator))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginRateLimitedAfterFiveAttempts() throws Exception {
        String ip = "10.9.9.9";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"));
    }

    @Test
    void noOpenRegistration() throws Exception {
        // v1's hole stays closed: there is no /auth/register. The request is
        // rejected (401 for anonymous callers hitting denyAll) and — critically —
        // no user is created.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(users.findByUsername("mallory")).isEmpty();
    }

    @Test
    void publicEndpointsStillOpen() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/status")).andExpect(status().isOk());
    }
}
