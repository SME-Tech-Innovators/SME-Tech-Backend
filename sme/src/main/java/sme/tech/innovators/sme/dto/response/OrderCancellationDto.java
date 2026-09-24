package sme.tech.innovators.sme.dto.response;

import java.time.LocalDateTime;

public record OrderCancellationDto(String status, String reason, String reviewNote,
        LocalDateTime requestedAt, LocalDateTime reviewedAt, boolean canRequest,
        String unavailableReason) {}
