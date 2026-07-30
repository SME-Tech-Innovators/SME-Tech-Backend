package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.StorefrontTemplateDto;
import sme.tech.innovators.sme.service.StorefrontTemplateService;

import java.util.List;

@Tag(name = "Storefront Templates", description = "Template gallery catalog for the storefront picker")
@RestController
@RequestMapping("/api/v1/storefront-templates")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class StorefrontTemplateController {

    private final StorefrontTemplateService storefrontTemplateService;

    @Operation(summary = "List storefront templates",
            description = "Returns available and coming_soon templates for the merchant template picker.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StorefrontTemplateDto>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.success(storefrontTemplateService.listTemplates()));
    }
}
