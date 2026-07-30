package sme.tech.innovators.sme.service;

import org.springframework.stereotype.Component;
import sme.tech.innovators.sme.exception.InvalidPublishConfigException;
import sme.tech.innovators.sme.exception.InvalidStorefrontConfigException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a storefront draft config against the template's supported sections.
 * All validation rules from Step 01 are enforced here.
 */
@Component
public class StorefrontConfigValidator {

    /**
     * Validates a config map against the given template constraints.
     *
     * @param config            the draft config to validate
     * @param supportedSections list of allowed section type strings from the template version
     * @param supportedThemes   list of allowed theme ID strings from the template version
     */
    public void validate(Map<String, Object> config,
                         List<String> supportedSections,
                         List<String> supportedThemes) {
        if (config == null) {
            throw new InvalidStorefrontConfigException("config must not be null");
        }

        validateConfigVersion(config);
        validateSections(config, supportedSections);
        validatePages(config);
        validateTheme(config, supportedThemes);
    }

    /**
     * Stricter validation used before creating a publish snapshot.
     * Requires shopName and themeId in addition to draft rules.
     */
    public void validateForPublish(Map<String, Object> config,
                                   List<String> supportedSections,
                                   List<String> supportedThemes) {
        if (config == null || config.isEmpty()) {
            throw new InvalidPublishConfigException("Draft config is required before publishing");
        }

        try {
            validate(config, supportedSections, supportedThemes);
        } catch (InvalidStorefrontConfigException ex) {
            throw new InvalidPublishConfigException(ex.getMessage());
        }

        Object shopName = config.get("shopName");
        if (shopName == null || shopName.toString().isBlank()) {
            throw new InvalidPublishConfigException("shopName is required before publishing");
        }

        Object themeId = config.get("themeId");
        if (themeId == null || themeId.toString().isBlank()) {
            throw new InvalidPublishConfigException("themeId is required before publishing");
        }
        if (!supportedThemes.contains(themeId.toString())) {
            throw new InvalidPublishConfigException(
                    "Unsupported themeId '" + themeId + "'. Supported themes: " + supportedThemes);
        }
    }

    // -------------------------------------------------------------------------
    // Private validators
    // -------------------------------------------------------------------------

    private void validateConfigVersion(Map<String, Object> config) {
        if (!config.containsKey("configVersion")) {
            throw new InvalidStorefrontConfigException("configVersion is required in config");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSections(Map<String, Object> config, List<String> supportedSections) {
        Object sectionsObj = config.get("sections");
        if (sectionsObj == null) {
            // sections is optional — skip validation if absent
            return;
        }
        if (!(sectionsObj instanceof List)) {
            throw new InvalidStorefrontConfigException("sections must be an array");
        }

        List<?> sections = (List<?>) sectionsObj;
        Set<String> supportedSet = Set.copyOf(supportedSections);

        for (int i = 0; i < sections.size(); i++) {
            Object sectionObj = sections.get(i);
            if (!(sectionObj instanceof Map)) {
                throw new InvalidStorefrontConfigException(
                        "Each section must be an object (index " + i + ")");
            }
            Map<String, Object> section = (Map<String, Object>) sectionObj;

            if (!section.containsKey("id") || section.get("id") == null) {
                throw new InvalidStorefrontConfigException(
                        "Section at index " + i + " is missing required field: id");
            }
            String type = (String) section.get("type");
            if (type == null || type.isBlank()) {
                throw new InvalidStorefrontConfigException(
                        "Section at index " + i + " is missing required field: type");
            }
            if (!supportedSet.contains(type)) {
                throw new InvalidStorefrontConfigException(
                        "Unsupported section type '" + type + "'. Supported types: " + supportedSections);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validatePages(Map<String, Object> config) {
        Object pagesObj = config.get("pages");
        if (pagesObj == null) {
            // pages is optional — skip if absent
            return;
        }
        if (!(pagesObj instanceof List)) {
            throw new InvalidStorefrontConfigException("pages must be an array");
        }
        List<?> pages = (List<?>) pagesObj;
        List<String> slugs = pages.stream()
                .filter(p -> p instanceof Map)
                .map(p -> (Map<String, Object>) p)
                .map(p -> (String) p.get("slug"))
                .filter(s -> s != null)
                .collect(Collectors.toList());

        Set<String> uniqueSlugs = Set.copyOf(slugs);
        if (uniqueSlugs.size() < slugs.size()) {
            throw new InvalidStorefrontConfigException("Custom page slugs must be unique within the config");
        }
    }

    private void validateTheme(Map<String, Object> config, List<String> supportedThemes) {
        Object themeObj = config.get("themeId");
        if (themeObj == null) {
            // themeId is optional on draft — skip if absent
            return;
        }
        String themeId = themeObj.toString();
        if (!supportedThemes.contains(themeId)) {
            throw new InvalidStorefrontConfigException(
                    "Unsupported themeId '" + themeId + "'. Supported themes: " + supportedThemes);
        }
    }
}
