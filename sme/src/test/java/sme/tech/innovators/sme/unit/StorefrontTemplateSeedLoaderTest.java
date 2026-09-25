package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedDefinition;
import sme.tech.innovators.sme.config.StorefrontTemplateSeedLoader;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;
import sme.tech.innovators.sme.service.StorefrontConfigValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontTemplateSeedLoaderTest {

    private final StorefrontTemplateSeedLoader loader =
            new StorefrontTemplateSeedLoader(new ObjectMapper());

    @Test
    void loadsAllBuiltInTemplateSeedsFromClasspath() {
        List<StorefrontTemplateSeedDefinition> definitions = loader.loadAll();

        assertThat(definitions).extracting(StorefrontTemplateSeedDefinition::getId)
                .containsExactly("artisan-atelier", "chapter-bookshop", "classic-boutique", "maison-editorial", "minimal-catalogue");
        assertThat(definitions).allSatisfy(definition -> {
            assertThat(definition.getName()).isNotBlank();
            assertThat(definition.getDescription()).isNotBlank();
            assertThat(definition.getVibe()).isNotBlank();
            assertThat(definition.getStatus()).isEqualTo(
                    "artisan-atelier".equals(definition.getId())
                            ? StorefrontTemplateStatus.DISABLED : StorefrontTemplateStatus.AVAILABLE);
            assertThat(definition.getDefaultConfig()).isNotEmpty();
            assertThat(definition.getDefaultConfig()).containsKey("configVersion");
        });
    }
    @Test
    void editorialSeedCanBePublishedWithItsDeclaredCapabilities() {
        StorefrontTemplateSeedDefinition maison = loader.loadAll().stream()
                .filter(seed -> "maison-editorial".equals(seed.getId()))
                .findFirst().orElseThrow();
        new StorefrontConfigValidator().validateForPublish(
                maison.getDefaultConfig(), maison.getSupportedSections(), maison.getSupportedThemes());
    }

    @Test
    void bookshopSeedCanBePublishedWithItsDeclaredCapabilities() {
        StorefrontTemplateSeedDefinition chapter = loader.loadAll().stream()
                .filter(seed -> "chapter-bookshop".equals(seed.getId()))
                .findFirst().orElseThrow();
        new StorefrontConfigValidator().validateForPublish(
                chapter.getDefaultConfig(), chapter.getSupportedSections(), chapter.getSupportedThemes());
    }

}
