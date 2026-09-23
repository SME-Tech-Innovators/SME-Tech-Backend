package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.ShippingQuoteRequest;
import sme.tech.innovators.sme.dto.response.ShippingQuoteDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.CartNotFoundException;
import sme.tech.innovators.sme.exception.CheckoutValidationException;
import sme.tech.innovators.sme.exception.ShippingNotConfiguredException;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;
import sme.tech.innovators.sme.integration.bobgo.BobGoPayloadBuilder;
import sme.tech.innovators.sme.integration.bobgo.BobGoRateMapper;
import sme.tech.innovators.sme.repository.CartRepository;
import sme.tech.innovators.sme.shipping.ShippingMoneyUtils;
import sme.tech.innovators.sme.shipping.ShippingQuoteCache;

import sme.tech.innovators.sme.integration.bobgo.BobGoRateMapper.ParsedBobGoRate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingQuoteService {

    private final PublicStoreResolver publicStoreResolver;
    private final CartRepository cartRepository;
    private final WorkspaceShippingSettingsService shippingSettingsService;
    private final BobGoClient bobGoClient;
    private final BobGoPayloadBuilder payloadBuilder;
    private final BobGoRateMapper rateMapper;
    private final ShippingQuoteCache quoteCache;

    @Transactional(readOnly = true)
    public ShippingQuoteDto quote(String storeSlug, ShippingQuoteRequest request) {
        Workspace workspace = publicStoreResolver.requireLiveWorkspace(storeSlug);
        UUID cartId = parseCartId(request.getCartId());

        Cart cart = cartRepository.findByIdAndWorkspaceIdAndStatus(cartId, workspace.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Cart not found or already used: " + cartId));

        WorkspaceShippingSettings settings = shippingSettingsService.findSettings(workspace.getId())
                .orElse(null);
        if (settings == null || !settings.isEnabled()) {
            throw new ShippingNotConfiguredException("Shipping is not enabled for this store");
        }

        List<ShippingQuoteDto.ShippingOptionDto> options = new ArrayList<>();
        String currency = cart.getCurrency() != null ? cart.getCurrency() : "ZAR";

        if (hasCollectionAddress(settings.getCollectionAddress())) {
            options.addAll(fetchBobGoOptions(
                    workspace.getId(), workspace.getName(), cart, settings, request, currency));
        } else if (settings.getFallbackFlatRateAmount() == null && !settings.isAllowPickup()) {
            throw new ShippingNotConfiguredException("Collection address is required for Bob Go shipping quotes");
        }

        if (settings.getFallbackFlatRateAmount() != null) {
            int minor = ShippingMoneyUtils.majorToMinor(settings.getFallbackFlatRateAmount());
            options.add(ShippingQuoteDto.ShippingOptionDto.builder()
                    .id("manual:flat")
                    .provider("manual")
                    .label("Standard delivery")
                    .amount(minor)
                    .currency(settings.getFallbackFlatRateCurrency() != null
                            ? settings.getFallbackFlatRateCurrency() : currency)
                    .build());
        }

        if (settings.isAllowPickup()) {
            String label = settings.getPickupLabel() != null && !settings.getPickupLabel().isBlank()
                    ? settings.getPickupLabel() : "Store pickup";
            options.add(ShippingQuoteDto.ShippingOptionDto.builder()
                    .id("pickup")
                    .provider("manual")
                    .label(label)
                    .amount(0)
                    .currency(currency)
                    .build());
        }

        if (options.isEmpty()) {
            throw new ShippingNotConfiguredException("No shipping options available for this store");
        }

        return ShippingQuoteDto.builder().options(options).build();
    }

    private List<ShippingQuoteDto.ShippingOptionDto> fetchBobGoOptions(UUID workspaceId,
                                                                        String workspaceName,
                                                                        Cart cart,
                                                                        WorkspaceShippingSettings settings,
                                                                        ShippingQuoteRequest request,
                                                                        String currency) {
        Map<String, Object> delivery = payloadBuilder.deliveryAddressFromQuote(request.getShippingAddress());
        Map<String, Object> collection = settings.getCollectionAddress();
        List<Map<String, Object>> parcels = payloadBuilder.parcelsFromCart(cart);
        BigDecimal declaredValue = cartSubtotal(cart);

        Map<String, Object> ratesPayload = payloadBuilder.buildRatesPayload(
                collection,
                delivery,
                parcels,
                declaredValue,
                workspaceName,
                "shipping@store.local",
                "+27800000000",
                null,
                null,
                null
        );

        List<ParsedBobGoRate> parsed = requestBobGoRates(workspaceId, ratesPayload, settings, currency);
        List<ShippingQuoteDto.ShippingOptionDto> options = new ArrayList<>();
        for (ParsedBobGoRate rate : parsed) {
            String token = quoteCache.store(ShippingQuoteCache.CachedQuote.create(
                    workspaceId,
                    cart.getId(),
                    rate.optionId(),
                    "bobgo",
                    rate.amountMinor(),
                    rate.currency(),
                    rate.label(),
                    rate.shipmentMeta(),
                    ratesPayload
            ));
            options.add(ShippingQuoteDto.ShippingOptionDto.builder()
                    .id(rate.optionId())
                    .provider("bobgo")
                    .label(rate.label())
                    .amount(rate.amountMinor())
                    .currency(rate.currency())
                    .estimatedDays(rate.estimatedDays())
                    .bobgoRateToken(token)
                    .build());
        }
        if (options.isEmpty()) {
            log.warn("Bob Go returned no courier rates for workspace={} (pickup/fallback may still apply)",
                    workspaceId);
        }
        return options;
    }

    /**
     * Bob Go sandbox often returns provider_rate_requests in {@code pending} with zero responses on the
     * first POST; a short retry usually returns priced options.
     */
    private List<ParsedBobGoRate> requestBobGoRates(UUID workspaceId,
                                                     Map<String, Object> ratesPayload,
                                                     WorkspaceShippingSettings settings,
                                                     String currency) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                Map<String, Object> response = bobGoClient.postRates(ratesPayload);
                List<ParsedBobGoRate> parsed = rateMapper.parseRatesResponse(response, currency);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
                if (attempt < 4) {
                    log.info("Bob Go rates empty for workspace={} attempt={}/4 — retrying",
                            workspaceId, attempt);
                    Thread.sleep(600L * attempt);
                }
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Bob Go quote failed for workspace={} attempt={}: {}",
                        workspaceId, attempt, ex.getMessage());
                if (attempt < 4) {
                    try {
                        Thread.sleep(600L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (lastFailure != null && settings.getFallbackFlatRateAmount() == null && !settings.isAllowPickup()) {
            throw lastFailure;
        }
        return List.of();
    }

    private static boolean hasCollectionAddress(Map<String, Object> address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        Object line1 = address.get("line1");
        return line1 != null && !line1.toString().isBlank();
    }

    private static BigDecimal cartSubtotal(Cart cart) {
        if (cart.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return cart.getItems().stream()
                .map(i -> i.getUnitPriceAmount().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static UUID parseCartId(String cartId) {
        try {
            return UUID.fromString(cartId);
        } catch (IllegalArgumentException ex) {
            throw new CheckoutValidationException("Invalid cartId format");
        }
    }
}
