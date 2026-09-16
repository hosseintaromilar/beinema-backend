package com.coupleai.coupleai.beinema.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Path uploadRoot;

    public FileStorageService(
            @Value("${beinema.upload.dir:uploads}") String uploadDir
    ) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storeAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("فایل عکس انتخاب نشده است");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("حجم عکس باید کمتر از ۲ مگابایت باشد");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("فقط فایل‌های JPG، PNG و WEBP پذیرفته می‌شوند");
        }

        String extension = extensionOf(file.getOriginalFilename(), contentType);
        String filename = userId + "-" + UUID.randomUUID() + extension;
        Path avatarsDir = uploadRoot.resolve("avatars");

        try {
            Files.createDirectories(avatarsDir);
            Path target = avatarsDir.resolve(filename);
            file.transferTo(target.toFile());
            return "/uploads/avatars/" + filename;
        } catch (IOException exception) {
            throw new IllegalArgumentException("ذخیره عکس پروفایل ناموفق بود");
        }
    }

    public void deleteIfManaged(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/uploads/")) {
            return;
        }
        Path target = uploadRoot.resolve(publicPath.replaceFirst("^/uploads/", "")).normalize();
        if (!target.startsWith(uploadRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            /* leftover files are harmless */
        }
    }

    private static String extensionOf(String originalName, String contentType) {
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png") || contentType.contains("png")) {
            return ".png";
        }
        if (name.endsWith(".webp") || contentType.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }
}
