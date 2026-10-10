package com.clubrating.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class Config {

    public final String botToken;
    public final String botUsername;
    public final Set<Long> adminIds;
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    private Config(String botToken, String botUsername, Set<Long> adminIds, String dbUrl, String dbUser, String dbPassword) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.adminIds = adminIds;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public boolean isAdmin(long telegramId) {
        return adminIds.contains(telegramId);
    }

    public static Config load() throws IOException {
        Properties fileProps = new Properties();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                fileProps.load(in);
            }
        }

        String token = get(fileProps, "BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("BOT_TOKEN is not set (env var BOT_TOKEN or config.properties)");
        }

        String admins = get(fileProps, "ADMIN_IDS");
        String adminsRaw = admins == null ? "" : admins;
        Set<Long> adminIds = Arrays.stream(adminsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException e) {
                        System.err.println("ADMIN_IDS: пропущено нечисловое значение «" + s + "»");
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (adminIds.isEmpty()) {
            System.err.println("Внимание: ADMIN_IDS пуст — админ-панель будет недоступна никому.");
        }

        String botUsername = getOrDefault(fileProps, "BOT_USERNAME", "");

        String dbUrl = getOrDefault(fileProps, "DB_URL",
                "jdbc:h2:file:./club_rating;MODE=MySQL;DB_CLOSE_ON_EXIT=TRUE");
        String dbUser = getOrDefault(fileProps, "DB_USER", "sa");
        String dbPassword = getOrDefault(fileProps, "DB_PASSWORD", "");

        return new Config(token, botUsername, adminIds, dbUrl, dbUser, dbPassword);
    }

    private static String get(Properties props, String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return props.getProperty(key);
    }

    private static String getOrDefault(Properties props, String key, String fallback) {
        String value = get(props, key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
