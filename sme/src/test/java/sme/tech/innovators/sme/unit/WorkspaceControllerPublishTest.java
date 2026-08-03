package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import sme.tech.innovators.sme.config.SecurityConfig;
import sme.tech.innovators.sme.controller.WorkspaceController;
import sme.tech.innovators.sme.dto.request.PublishStorefrontRequest;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.UserRole;
import sme.tech.innovators.sme.entity.WorkspaceStatus;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.security.CustomUserDetailsService;
import sme.tech.innovators.sme.security.JwtAuthenticationFilter;
import sme.tech.innovators.sme.service.JwtService;
import sme.tech.innovators.sme.service.RateLimitService;
import sme.tech.innovators.sme.service.WorkspaceService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkspaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
class WorkspaceControllerPublishTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private RateLimitService rateLimitService;

    private UUID userId;
    private UUID workspaceId;
    private UUID storefrontId;
    private UUID snapshotId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        storefrontId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("$2a$12$hashed")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .build();

        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        when(userRepository.findByEmailAndIsDeletedFalse("owner@example.com"))
                .thenReturn(Optional.of(user));
    }

    // -------------------------------------------------------------------------
    // POST /storefront/publish
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns200WithPublishResult() throws Exception {
        PublishResultDto result = PublishResultDto.builder()
                .workspaceId(workspaceId)
                .storefrontId(storefrontId)
                .publishedSnapshotId(snapshotId)
                .status(WorkspaceStatus.LIVE)
                .publishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .build();

        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any(PublishStorefrontRequest.class)))
                .thenReturn(result);

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);
        request.setNotes("Initial launch");

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.storefrontId").value(storefrontId.toString()))
                .andExpect(jsonPath("$.data.publishedSnapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data.status").value("LIVE"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns400_whenConfirmMissing() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns400_whenConfirmationRequired() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new PublishConfirmationRequiredException("Publishing requires confirm=true"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(false);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PUBLISH_CONFIRMATION_REQUIRED"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns404_whenDraftMissing() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new StorefrontDraftNotFoundException("Draft config is missing"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STOREFRONT_DRAFT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns400_whenInvalidPublishConfig() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new InvalidPublishConfigException("shopName is required before publishing"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PUBLISH_CONFIG"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns404_whenWorkspaceNotOwned() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new WorkspaceNotFoundException("Workspace not found or you do not have access"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns422_whenTemplateDisabled() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new TemplateDisabledException("Template is not available"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEMPLATE_DISABLED"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void publish_returns409_whenPublicSlugUnavailable() throws Exception {
        when(workspaceService.publishStorefront(eq(workspaceId), eq(userId), any()))
                .thenThrow(new PublicSlugUnavailableException("Failed to generate a unique public slug"));

        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PUBLIC_SLUG_UNAVAILABLE"));
    }

    @Test
    void publish_returns401_whenNotAuthenticated() throws Exception {
        PublishStorefrontRequest request = new PublishStorefrontRequest();
        request.setConfirm(true);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/publish", workspaceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // -------------------------------------------------------------------------
    // GET /storefront/published
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublished_returns200WithSnapshot() throws Exception {
        PublishedStorefrontDto dto = PublishedStorefrontDto.builder()
                .workspaceId(workspaceId)
                .storefrontId(storefrontId)
                .publishedSnapshotId(snapshotId)
                .templateId("classic-boutique")
                .templateVersion(1)
                .configVersion(1)
                .config(Map.of("shopName", "My Store", "themeId", "blue"))
                .status(WorkspaceStatus.LIVE)
                .publicSlug("nkandu-fashion")
                .publishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .notes("Initial launch")
                .build();

        when(workspaceService.getPublishedStorefront(workspaceId, userId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/published", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publishedSnapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data.publicSlug").value("nkandu-fashion"))
                .andExpect(jsonPath("$.data.config.shopName").value("My Store"))
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublished_returns404_whenNotPublished() throws Exception {
        when(workspaceService.getPublishedStorefront(workspaceId, userId))
                .thenThrow(new PublishedStorefrontNotFoundException("No published storefront found"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/published", workspaceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PUBLISHED_STOREFRONT_NOT_FOUND"));
    }

    @Test
    void getPublished_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/published", workspaceId))
                .andExpect(status().is4xxClientError());
    }

    // -------------------------------------------------------------------------
    // GET /storefront/publish-history
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublishHistory_returnsMetadataOnly() throws Exception {
        PublishHistoryItemDto item = PublishHistoryItemDto.builder()
                .snapshotId(snapshotId)
                .templateId("classic-boutique")
                .templateVersion(1)
                .configVersion(1)
                .publishedByUserId(userId)
                .publishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .notes("Initial launch")
                .build();

        when(workspaceService.getPublishHistory(workspaceId, userId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/publish-history", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].snapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data[0].notes").value("Initial launch"))
                .andExpect(jsonPath("$.data[0].config").doesNotExist())
                .andExpect(jsonPath("$.data[0].publishedByUserId").value(userId.toString()));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublishHistory_returnsEmptyArray_whenNone() throws Exception {
        when(workspaceService.getPublishHistory(workspaceId, userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/publish-history", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // -------------------------------------------------------------------------
    // GET /storefront/publish-history/{snapshotId}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublishSnapshot_returnsFullConfig() throws Exception {
        PublishedStorefrontDto dto = PublishedStorefrontDto.builder()
                .workspaceId(workspaceId)
                .storefrontId(storefrontId)
                .publishedSnapshotId(snapshotId)
                .templateId("classic-boutique")
                .templateVersion(1)
                .configVersion(1)
                .config(Map.of("shopName", "My Store"))
                .status(WorkspaceStatus.LIVE)
                .publicSlug("nkandu-fashion")
                .publishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .notes("v1")
                .build();

        when(workspaceService.getPublishSnapshot(workspaceId, snapshotId, userId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/publish-history/{sid}",
                        workspaceId, snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedSnapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data.config.shopName").value("My Store"));
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void getPublishSnapshot_returns404_whenMissing() throws Exception {
        when(workspaceService.getPublishSnapshot(workspaceId, snapshotId, userId))
                .thenThrow(new PublishedStorefrontNotFoundException("Publish snapshot not found"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/storefront/publish-history/{sid}",
                        workspaceId, snapshotId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PUBLISHED_STOREFRONT_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // POST /storefront/unpublish
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void unpublish_returns200WithUnpublishedStatus() throws Exception {
        UnpublishResultDto result = UnpublishResultDto.builder()
                .workspaceId(workspaceId)
                .status(WorkspaceStatus.UNPUBLISHED)
                .lastPublishedAt(LocalDateTime.of(2026, 5, 22, 8, 0))
                .build();

        when(workspaceService.unpublishStorefront(workspaceId, userId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/unpublish", workspaceId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void unpublish_returns404_whenNeverPublished() throws Exception {
        when(workspaceService.unpublishStorefront(workspaceId, userId))
                .thenThrow(new PublishedStorefrontNotFoundException("Cannot unpublish: no published snapshot"));

        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/unpublish", workspaceId)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PUBLISHED_STOREFRONT_NOT_FOUND"));
    }

    @Test
    void unpublish_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{id}/storefront/unpublish", workspaceId)
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}
