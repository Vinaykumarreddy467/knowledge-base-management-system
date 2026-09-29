package com.kbms.document;

import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Private local storage. Callers get a generated {@code storageName}; the original filename is
 * metadata only, so no user-supplied path segment ever reaches the filesystem.
 */
@Service
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(AppProperties properties) {
        this.root = Paths.get(properties.storage().directory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create KBMS storage directory " + root, ex);
        }
    }

    public String newStorageName(FileType type) {
        return UUID.randomUUID().toString().replace("-", "") + "." + type.extension();
    }

    public void save(String storageName, InputStream in) {
        try {
            Files.copy(in, resolve(storageName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(500, "STORAGE_FAILURE", "Could not store the uploaded file");
        }
    }

    public Path resolve(String storageName) {
        Path path = root.resolve(storageName).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Invalid storage reference");
        }
        return path;
    }

    public boolean exists(String storageName) {
        return Files.exists(resolve(storageName));
    }

    public byte[] read(String storageName) {
        try {
            return Files.readAllBytes(resolve(storageName));
        } catch (IOException ex) {
            throw ApiException.notFound("Stored file is no longer available");
        }
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(resolve(storageName));
        } catch (IOException ex) {
            throw new ApiException(500, "STORAGE_FAILURE", "Could not remove the stored file");
        }
    }

    /** Test/ops helper: removes every stored file (never called from request handling). */
    public void purge() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
        }
    }
}
