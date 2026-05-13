package dev.backline.cli.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ResourceCopier {

    private ResourceCopier() {}

    public static void copyClasspathResource(String classpathPath, Path target) throws IOException {
        try (InputStream in = ResourceCopier.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IOException("missing classpath resource: " + classpathPath);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
