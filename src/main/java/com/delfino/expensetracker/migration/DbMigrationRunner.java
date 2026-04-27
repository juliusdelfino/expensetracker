package com.delfino.expensetracker.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Migrates data from a local SQLite database into the configured PostgreSQL datasource.
 *
 * Activate with the "migrate" Spring profile:
 *
 *   java -jar expensetracker.jar \
 *       --spring.profiles.active=migrate \
 *       --migrate.sqlite.path=data/expensetracker.db
 *
 * Optional flags:
 *   --migrate.dry-run=true    (default: false) — print counts without writing
 *   --migrate.clear=true      (default: false) — TRUNCATE all PG tables before insert
 *
 * Prerequisites:
 *   - SQLite JDBC driver on the classpath (org.xerial:sqlite-jdbc, provided via pom.xml migrate profile).
 *   - PostgreSQL schema already exists (run the app normally once so ddl-auto=update creates the tables).
 *   - The app exits automatically after migration completes.
 */
@Component
@Profile("migrate")
public class DbMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DbMigrationRunner.class);

    /** Tables in dependency order: parents before children. */
    private static final List<String> TABLES = List.of(
            "users",
            "stores",
            "expenses",
            "expense_items",
            "expense_tags",
            "expense_attachments",
            "chat_messages",
            "chat_message_linked_expenses"
    );

    /** Tables that have a serial/identity "id" column whose sequence must be reset. */
    private static final List<String> ID_TABLES = List.of(
            "users", "stores", "expenses", "expense_items", "chat_messages"
    );

    private final DataSource pgDataSource;

    @Value("${migrate.sqlite.path}")
    private String sqlitePath;

    @Value("${migrate.dry-run:false}")
    private boolean dryRun;

    @Value("${migrate.clear:false}")
    private boolean clear;

    public DbMigrationRunner(DataSource pgDataSource) {
        this.pgDataSource = pgDataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== SQLite → PostgreSQL Migration ===");
        log.info("Source : {}", sqlitePath);
        log.info("Dry-run: {}", dryRun);
        log.info("Clear  : {}", clear);

        String sqliteUrl = "jdbc:sqlite:" + sqlitePath;

        try (Connection sqlite = DriverManager.getConnection(sqliteUrl);
             Connection pg = pgDataSource.getConnection()) {

            pg.setAutoCommit(false);

            try {
                if (clear && !dryRun) {
                    log.info("Clearing PostgreSQL tables...");
                    // Truncate in reverse dependency order
                    List<String> reversed = new ArrayList<>(TABLES);
                    java.util.Collections.reverse(reversed);
                    try (Statement st = pg.createStatement()) {
                        for (String table : reversed) {
                            if (tableExistsInPg(pg, table)) {
                                st.execute("TRUNCATE TABLE \"" + table + "\" CASCADE");
                                log.info("  Truncated: {}", table);
                            }
                        }
                    }
                    pg.commit();
                }

                int grandTotal = 0;
                for (String table : TABLES) {
                    grandTotal += migrateTable(sqlite, pg, table);
                }

                if (!dryRun) {
                    resetSequences(pg);
                    pg.commit();
                }

                log.info("=== Migration {} — {} total rows ===",
                        dryRun ? "DRY-RUN complete" : "complete", grandTotal);

            } catch (Exception e) {
                pg.rollback();
                log.error("Migration failed — rolled back", e);
                throw e;
            }
        }

        log.info("Done. Shutting down.");
        System.exit(0);
    }

    private int migrateTable(Connection sqlite, Connection pg, String table) throws SQLException {
        if (!tableExistsInSqlite(sqlite, table)) {
            log.info("[SKIP] {} — not found in SQLite", table);
            return 0;
        }

        List<String> cols = getColumns(sqlite, table);
        Set<String> timestampCols = getTimestampColumns(pg, table);
        Set<String> booleanCols = getBooleanColumns(pg, table);

        String selectSql = "SELECT " + String.join(", ", cols) + " FROM \"" + table + "\"";
        String colList   = String.join(", ", cols.stream().map(c -> "\"" + c + "\"").toList());
        String placeholders = "?,".repeat(cols.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String insertSql = "INSERT INTO \"" + table + "\" (" + colList + ") VALUES (" + placeholders + ") ON CONFLICT DO NOTHING";

        int count = 0;
        try (Statement st = sqlite.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {

            if (dryRun) {
                while (rs.next()) count++;
                log.info("[DRY-RUN] {}: {} rows — SQL: {}", table, count, insertSql);
                return count;
            }

            try (PreparedStatement ps = pg.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 0; i < cols.size(); i++) {
                        String col = cols.get(i);
                        Object val = rs.getObject(col);
                        if (val != null && timestampCols.contains(col) && val instanceof Number num) {
                            val = LocalDateTime.ofInstant(Instant.ofEpochMilli(num.longValue()), ZoneOffset.UTC);
                        } else if (val != null && booleanCols.contains(col) && val instanceof Number num) {
                            val = num.intValue() != 0;
                        }
                        ps.setObject(i + 1, val);
                    }
                    ps.addBatch();
                    count++;
                    if (count % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
        }

        log.info("[OK] {}: {} rows", table, count);
        return count;
    }

    /** Returns column names whose JDBC type is BOOLEAN in the PostgreSQL table. */
    private Set<String> getBooleanColumns(Connection pg, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = pg.getMetaData().getColumns(null, "public", table, null)) {
            while (rs.next()) {
                if (rs.getInt("DATA_TYPE") == Types.BIT || rs.getInt("DATA_TYPE") == Types.BOOLEAN) {
                    result.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return result;
    }

    /** Returns column names whose JDBC type is TIMESTAMP in the PostgreSQL table. */
    private Set<String> getTimestampColumns(Connection pg, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = pg.getMetaData().getColumns(null, "public", table, null)) {
            while (rs.next()) {
                int jdbcType = rs.getInt("DATA_TYPE");
                if (jdbcType == Types.TIMESTAMP || jdbcType == Types.TIMESTAMP_WITH_TIMEZONE) {
                    result.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return result;
    }

    private void resetSequences(Connection pg) throws SQLException {
        for (String table : ID_TABLES) {
            if (!tableExistsInPg(pg, table)) continue;
            String sql = "SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), " +
                         "COALESCE((SELECT MAX(id) FROM \"" + table + "\"), 1))";
            try (Statement st = pg.createStatement()) {
                st.execute(sql);
                log.info("[SEQ] Reset sequence for {}", table);
            }
        }
    }

    private List<String> getColumns(Connection sqlite, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        try (ResultSet rs = sqlite.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) cols.add(rs.getString("COLUMN_NAME"));
        }
        return cols;
    }

    private boolean tableExistsInSqlite(Connection sqlite, String table) throws SQLException {
        try (ResultSet rs = sqlite.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    private boolean tableExistsInPg(Connection pg, String table) throws SQLException {
        try (ResultSet rs = pg.getMetaData().getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}

