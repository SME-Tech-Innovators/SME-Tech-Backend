package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sme.tech.innovators.sme.dto.response.AnalyticsBreakdownsDto;
import sme.tech.innovators.sme.dto.response.AnalyticsSummaryDto;
import sme.tech.innovators.sme.dto.response.AnalyticsTimeseriesDto;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.WorkspaceAnalyticsService;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Workspace Analytics", description = "Merchant dashboard KPIs, timeseries, and breakdowns")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspaceAnalyticsController {

    private final WorkspaceAnalyticsService analyticsService;
    private final UserRepository userRepository;

    @Operation(summary = "Summary KPIs for the date range (catalog stock counts are current snapshot)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> summary(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.summary(workspaceId, resolveUserId(auth), from, to)));
    }

    @Operation(summary = "Paid revenue / order timeseries (grain=day)")
    @GetMapping("/timeseries")
    public ResponseEntity<ApiResponse<AnalyticsTimeseriesDto>> timeseries(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String grain,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.timeseries(workspaceId, resolveUserId(auth), from, to, grain)));
    }

    @Operation(summary = "Order status / payment / top products / category revenue breakdowns")
    @GetMapping("/breakdowns")
    public ResponseEntity<ApiResponse<AnalyticsBreakdownsDto>> breakdowns(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.breakdowns(workspaceId, resolveUserId(auth), from, to)));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
