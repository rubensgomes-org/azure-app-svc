package com.rubensgomes.azure.appsvc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Verifies that {@link OpenApiProperties} binds from the {@code openapi}
 * prefix.
 *
 * @author Rubens Gomes
 * @implNote This project's source code and documentation were generated
 *     with the assistance of Artificial Intelligence (AI). For more
 *     information, please refer to the `AI_DISCLAIMER.md` document located
 *     in the project's root directory.
 */
class OpenApiPropertiesTest {

  @Test
  @DisplayName("the openapi prefix binds onto the record components")
  void bindsFromTheOpenapiPrefix() {
    ConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            Map.of(
                "openapi.title", "Azure App Service Demo",
                "openapi.description", "Spring Boot demo deployed to Azure App Service",
                "openapi.version", "0.0.1-SNAPSHOT"));

    OpenApiProperties properties =
        new Binder(source).bind("openapi", OpenApiProperties.class).get();

    assertThat(properties.title()).isEqualTo("Azure App Service Demo");
    assertThat(properties.description())
        .isEqualTo("Spring Boot demo deployed to Azure App Service");
    assertThat(properties.version()).isEqualTo("0.0.1-SNAPSHOT");
  }
}
