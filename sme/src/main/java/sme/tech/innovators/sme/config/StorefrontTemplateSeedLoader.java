package sme.tech.innovators.sme.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorefrontTemplateSeedLoader {

    static final String TEMPLATE_GLOB = "classpath:storefront-templates/*.json";

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    List<StorefrontTemplateSeedDefinition> loadAll() {
        List<StorefrontTemplateSeedDefinition> definitions = new ArrayList<>();
        try {
            Resource[] resources = resourceResolver.getResources(TEMPLATE_GLOB);
            for (Resource resource : resources) {
                definitions.add(readDefinition(resource));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan storefront template seeds", e);
        }
        definitions.sort(Comparator.comparing(StorefrontTemplateSeedDefinition::getId));
        return definitions;
    }

    private StorefrontTemplateSeedDefinition readDefinition(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            StorefrontTemplateSeedDefinition definition =
                    objectMapper.readValue(input, StorefrontTemplateSeedDefinition.class);
            if (definition.getId() == null || definition.getId().isBlank()) {
                throw new IllegalStateException(
                        "Storefront template seed is missing id: " + resource.getFilename());
            }
            if (definition.getDefaultConfig() == null || definition.getDefaultConfig().isEmpty()) {
                throw new IllegalStateException(
                        "Storefront template seed is missing defaultConfig: " + definition.getId());
            }
            log.debug("Loaded storefront template seed: {}", definition.getId());
            return definition;
        } catch (IOException e) {
            throw new IOException("Failed to read storefront template seed: " + resource.getFilename(), e);
        }
    }
}
