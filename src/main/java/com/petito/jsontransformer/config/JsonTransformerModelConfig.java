package com.petito.jsontransformer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

@Configuration
public class JsonTransformerModelConfig {

    @Bean
    public Map<String, Object> mappingModel() {
        Yaml yaml = new Yaml();
        ClassPathResource resource = new ClassPathResource("mapper.yml");

        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> data = yaml.load(is);
            return data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile caricare mapper.yml all'avvio", e);
        }
    }
}