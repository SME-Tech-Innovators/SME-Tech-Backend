package sme.tech.innovators.sme.integration.bobgo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class BobGoRateMapper {

    public List<ParsedBobGoRate> parseRatesResponse(Map<String, Object> response, String currency) {
        if (response == null) {
            return List.of();
        }
        List<Map<String, Object>> rateRows = extractRateList(response);
        List<ParsedBobGoRate> parsed = new ArrayList<>();
        Object rateRequestId = response.get("rate_request_id");
        if (rateRequestId == null) {
            rateRequestId = response.get("id");
        }
        for (Map<String, Object> row : rateRows) {
            ParsedBobGoRate rate = parseRateRow(normalizeRateRow(row), currency, rateRequestId);
            if (rate != null) {
                parsed.add(rate);
            }
        }
        parsed.sort(Comparator.comparingInt(ParsedBobGoRate::amountMinor));
        return parsed;
    }

    public ParsedBobGoRate parseRateRow(Map<String, Object> row, String currency, Object rateRequestId) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        row = normalizeRateRow(row);
        int amountMinor = extractAmountMinor(row);
        if (amountMinor < 0) {
            return null;
        }
        String serviceCode = firstNonBlank(
                stringVal(row.get("service_level_code")),
                stringVal(row.get("service_code")),
                stringVal(row.get("code")));
        String label = firstNonBlank(
                stringVal(row.get("service_name")),
                stringVal(row.get("service_level_name")),
                stringVal(row.get("name")),
                serviceCode,
                "Standard delivery");
        String providerSlug = firstNonBlank(
                stringVal(row.get("provider_slug")),
                stringVal(row.get("provider")),
                stringVal(row.get("courier_slug")),
                "bobgo");
        if (serviceCode == null) {
            serviceCode = label;
        }
        String displayLabel = label;
        if (serviceCode != null && !serviceCode.equalsIgnoreCase(label)
                && !(row.get("service_level") instanceof String)) {
            displayLabel = label + " (" + serviceCode + ")";
        }
        Integer estimatedDays = extractEstimatedDays(row);
        String optionId = "bobgo:" + providerSlug + ":" + serviceCode + ":" + amountMinor;

        Map<String, Object> meta = new LinkedHashMap<>(row);
        if (rateRequestId != null) {
            meta.put("rate_request_id", rateRequestId);
        }
        meta.put("provider_slug", providerSlug);
        meta.put("service_level_code", serviceCode);

        return new ParsedBobGoRate(
                optionId,
                displayLabel,
                amountMinor,
                currency != null ? currency : "ZAR",
                estimatedDays,
                meta
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractRateList(Map<String, Object> response) {
        List<Map<String, Object>> fromProviders = extractProviderRateRequests(response);
        if (!fromProviders.isEmpty()) {
            return fromProviders;
        }
        for (String key : List.of("rates", "courier_rates", "services", "results")) {
            Object val = response.get(key);
            if (val instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> (Map<String, Object>) m)
                        .toList();
            }
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return extractRateList((Map<String, Object>) dataMap);
        }
        return List.of();
    }

    /** Bob Go v2 POST /rates returns rows under provider_rate_requests[].responses. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractProviderRateRequests(Map<String, Object> response) {
        Object requests = response.get("provider_rate_requests");
        if (!(requests instanceof List<?> requestList)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object reqObj : requestList) {
            if (!(reqObj instanceof Map<?, ?> reqMap)) {
                continue;
            }
            Map<String, Object> req = (Map<String, Object>) reqMap;
            String status = stringVal(req.get("status"));
            if (status != null
                    && !status.equalsIgnoreCase("success")
                    && !status.equalsIgnoreCase("pending")) {
                continue;
            }
            String providerSlug = firstNonBlank(
                    stringVal(req.get("provider_slug")),
                    stringVal(req.get("provider_name")),
                    "bobgo");
            Object responses = req.get("responses");
            if (!(responses instanceof List<?> responseList)) {
                continue;
            }
            for (Object respObj : responseList) {
                if (!(respObj instanceof Map<?, ?> respMap)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) respMap);
                String rowStatus = stringVal(row.get("status"));
                if (rowStatus != null
                        && !rowStatus.equalsIgnoreCase("success")
                        && !rowStatus.equalsIgnoreCase("pending")) {
                    continue;
                }
                row.putIfAbsent("provider_slug", providerSlug);
                rows.add(normalizeRateRow(row));
            }
        }
        return rows;
    }

    /** Bob Go often nests service metadata under service_level: { code, name, description, ... }. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeRateRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        Object level = row.get("service_level");
        if (level instanceof String label && !label.isBlank()) {
            putIfAbsent(normalized, "service_level_name", label);
        }
        if (level instanceof Map<?, ?> levelMap) {
            Map<String, Object> sl = (Map<String, Object>) levelMap;
            putIfAbsent(normalized, "service_level_code", sl.get("code"));
            putIfAbsent(normalized, "service_level_name", sl.get("name"));
            putIfAbsent(normalized, "code", sl.get("code"));
            putIfAbsent(normalized, "name", sl.get("name"));
            putIfAbsent(normalized, "description", sl.get("description"));
            Object days = sl.get("service_level_days");
            if (days instanceof Number n) {
                putIfAbsent(normalized, "estimated_delivery_days", n);
            }
        }
        return normalized;
    }

    private static void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        target.putIfAbsent(key, value);
    }

    private static int extractAmountMinor(Map<String, Object> row) {
        for (String key : List.of(
                "price", "amount", "total", "rate", "charge", "rate_amount", "rate_amount_excl_vat")) {
            Integer minor = toMinorUnits(row.get(key));
            if (minor != null && minor >= 0) {
                return minor;
            }
        }
        return -1;
    }

    private static Integer toMinorUnits(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d >= 1000) {
                return (int) Math.round(d);
            }
            return BigDecimal.valueOf(d).multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        try {
            String s = value.toString().trim();
            if (s.contains(".")) {
                return new BigDecimal(s).multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP).intValue();
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer extractEstimatedDays(Map<String, Object> row) {
        for (String key : List.of("estimated_delivery_days", "estimated_days", "delivery_days")) {
            Object val = row.get(key);
            if (val instanceof Number n) {
                return n.intValue();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            return null;
        }
        String s = value.toString().trim();
        if (s.startsWith("{") && s.contains("code=")) {
            return null;
        }
        return s.isEmpty() ? null : s;
    }

    public record ParsedBobGoRate(
            String optionId,
            String label,
            int amountMinor,
            String currency,
            Integer estimatedDays,
            Map<String, Object> shipmentMeta
    ) {}
}
