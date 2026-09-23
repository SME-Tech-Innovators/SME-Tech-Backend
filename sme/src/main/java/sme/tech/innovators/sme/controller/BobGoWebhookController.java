package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sme.tech.innovators.sme.service.BobGoWebhookService;

import java.util.Map;

@Tag(name = "Bob Go", description = "Bob Go platform webhooks")
@RestController
@RequestMapping("/api/v1/webhooks/bobgo")
@RequiredArgsConstructor
public class BobGoWebhookController {

    private final BobGoWebhookService bobGoWebhookService;

    @Operation(summary = "Bob Go webhook receiver (fulfillment / tracking topics)")
    @PostMapping
    public ResponseEntity<Map<String, Boolean>> webhook(
            @RequestHeader(value = "bobgo-webhook-signature", required = false) String signature,
            @RequestBody String rawBody) {
        bobGoWebhookService.handleWebhook(signature, rawBody);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
