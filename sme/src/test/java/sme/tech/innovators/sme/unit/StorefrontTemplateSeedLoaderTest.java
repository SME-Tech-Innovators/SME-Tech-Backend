package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedDefinition;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedLoader;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontTemplateSeedLoaderTest {

    private final StorefrontTemplateSeedLoader loader =
            new StorefrontTemplateSeedLoader(new ObjectMapper());

    @Test
    void loadsAllBuiltInTemplateSeedsFromClasspath() {
        List<StorefrontTemplateSeedDefinition> definitions = loader.loadAll();

        assertThat(definitions).extracting(StorefrontTemplateSeedDefinition::getId)
                .containsExactly("artisan-atelier", "classic-boutique", "minimal-catalogue");
        assertThat(definitions).allSatisfy(definition -> {
            assertThat(definition.getName()).isNotBlank();
            assertThat(definition.getDescription()).isNotBlank();
            assertThat(definition.getVibe()).isNotBlank();
            assertThat(definition.getStatus()).isEqualTo(StorefrontTemplateStatus.AVAILABLE);
            assertThat(definition.getDefaultConfig()).isNotEmpty();
            assertThat(definition.getDefaultConfig()).containsKey("configVersion");
        });
    }
}
