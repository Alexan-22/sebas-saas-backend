
package com.tiendasebas.tiendasebas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if ("local".equalsIgnoreCase(storageProvider)) {
            String ruta = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/uploads/**").addResourceLocations(ruta);
        } else if (!"supabase".equalsIgnoreCase(storageProvider)) {
            throw new IllegalStateException("Proveedor de almacenamiento no válido");
        }
    }
}
