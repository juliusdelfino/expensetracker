package com.delfino.expensetracker.util;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class ReceiptStorageUtils {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM");

    private ReceiptStorageUtils() {
    }

    public static String buildRelativePath(long userId, LocalDate bucketDate, UUID fileId, String originalFilename) {
        String extension = getExtension(originalFilename);
        return Path.of("receipts",
                        String.valueOf(userId),
                        YEAR_MONTH_FORMATTER.format(bucketDate),
                        fileId + extension)
                .toString()
                .replace('\\', '/');
    }

    public static String getExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "";
        String ext = filename.substring(dotIndex + 1)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return ext.isEmpty() ? "" : "." + ext;
    }

    public static Path resolveStoredPath(String dataDir, String storedPath) {
        Path path = Path.of(storedPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        String normalizedStoredPath = normalizeSeparators(path.normalize().toString());
        String normalizedDataDir = normalizeSeparators(Path.of(dataDir).normalize().toString());
        if (normalizedStoredPath.equals(normalizedDataDir) || normalizedStoredPath.startsWith(normalizedDataDir + "/")) {
            return path.toAbsolutePath().normalize();
        }
        return Path.of(dataDir).toAbsolutePath().normalize().resolve(path).normalize();
    }

    public static String normalizeSeparators(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    public static boolean isNewReceiptLayout(String storedPath) {
        String normalized = normalizeSeparators(storedPath);
        return normalized != null && normalized.matches("^receipts/\\d+/\\d{4}_\\d{2}/[^/]+$");
    }

    public static String relativizeToDataDir(String dataDir, Path absolutePath) {
        Path dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
        Path normalized = absolutePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataRoot)) {
            return normalizeSeparators(normalized.toString());
        }
        return normalizeSeparators(dataRoot.relativize(normalized).toString());
    }
}

