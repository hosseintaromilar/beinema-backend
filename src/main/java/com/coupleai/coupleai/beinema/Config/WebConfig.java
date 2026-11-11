package com.coupleai.coupleai.beinema.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${beinema.upload.dir:uploads}")
    private String uploadDir;

    private final com.coupleai.coupleai.beinema.lock.RoomLockInterceptor roomLockInterceptor;

    public WebConfig(com.coupleai.coupleai.beinema.lock.RoomLockInterceptor roomLockInterceptor) {
        this.roomLockInterceptor = roomLockInterceptor;
    }

    /** A locked conversation opens only with its key (RoomLockInterceptor). */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roomLockInterceptor).addPathPatterns("/api/chat-rooms/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Path.of(uploadDir).toAbsolutePath().normalize();
        String location = path.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        // Avatars, agent photos and blog pictures are public. Room files are served by ChatFileController.
        // Every upload gets a new random name (FileStorageService), so a file never changes under its
        // name: browsers may keep it for a year instead of downloading it on every page.
        CacheControl forever = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(location + "avatars/")
                .setCacheControl(forever);
        registry.addResourceHandler("/uploads/agents/**")
                .addResourceLocations(location + "agents/")
                .setCacheControl(forever);
        registry.addResourceHandler("/uploads/blog/**")
                .addResourceLocations(location + "blog/")
                .setCacheControl(forever);
    }
}
