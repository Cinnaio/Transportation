package com.github.cinnaio.transportation.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "transportation.db").getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("Transportation-SQLite");

        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void reload() {
        close();
        initialize();
    }

    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Garage Database (Core Table)
            stmt.execute("CREATE TABLE IF NOT EXISTS garage_vehicles (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_id VARCHAR(36) NOT NULL," + // Player UUID usually
                    "owner_name VARCHAR(16)," + // Player Name (optional but good for display)
                    "vehicle_identity_code VARCHAR(64) UNIQUE NOT NULL," +
                    "vehicle_model VARCHAR(64) NOT NULL," +
                    "vehicle_model_id VARCHAR(64) NOT NULL," +
                    "model_index INTEGER DEFAULT 1," +
                    "stats_original TEXT," + // JSON or serialized string for original stats
                    "stats_extended TEXT," + // JSON or serialized string for extended stats
                    "is_in_garage BOOLEAN DEFAULT 1," +
                    "is_frozen BOOLEAN DEFAULT 0," +
                    "is_destroyed BOOLEAN DEFAULT 0" +
                    ")");

            // 2. Server Vehicles (For Sale)
            stmt.execute("CREATE TABLE IF NOT EXISTS server_vehicles (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "vehicle_name VARCHAR(64) NOT NULL," +
                    "vehicle_model VARCHAR(64) NOT NULL," +
                    "stats_original TEXT," +
                    "stats_extended TEXT," +
                    "price DOUBLE NOT NULL" +
                    ")");

            // 3. Player Purchase Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_purchases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_id VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16)," +
                    "vehicle_type VARCHAR(64)," +
                    "amount DOUBLE," +
                    "success BOOLEAN," +
                    "fail_reason TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // 4. Vehicle Destruction Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_destruction (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "vehicle_identity_code VARCHAR(64)," +
                    "death_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "death_location VARCHAR(128)," +
                    "death_reason VARCHAR(128)" +
                    ")");

            // 5. Vehicle Garage In/Out Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_garage_ops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "operator_uuid VARCHAR(36)," +
                    "operator_name VARCHAR(16)," +
                    "vehicle_uuid VARCHAR(64)," + // Assuming identity code or internal UUID
                    "operation_type VARCHAR(16)," + // SUMMON / RECALL
                    "operation_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "success BOOLEAN," +
                    "fail_reason TEXT" +
                    ")");

            // 6. Ownership Operation Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_ownership_ops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "operator_id VARCHAR(36)," +
                    "operator_name VARCHAR(16)," +
                    "vehicle_identity_code VARCHAR(64)," +
                    "vehicle_model VARCHAR(64)," +
                    "operation_category VARCHAR(32)," + // BUY, BIND, UNBIND, TRANSFER, FREEZE, UNFREEZE
                    "operation_content TEXT," +
                    "operation_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "success BOOLEAN," +
                    "fail_reason TEXT" +
                    ")");

            // 7. Usage Rights Operation Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_usage_ops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_id VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "vehicle_identity_code VARCHAR(64)," +
                    "operation_type VARCHAR(16)," + // ENTER / EXIT
                    "location VARCHAR(128)," +
                    "operation_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "has_permission BOOLEAN" +
                    ")");

            // 8. Vehicle Identity Code Change Logs
            stmt.execute("CREATE TABLE IF NOT EXISTS log_identity_changes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_id VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "old_identity_code VARCHAR(64)," +
                    "new_identity_code VARCHAR(64)," +
                    "operation_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
                    
            // Migration: Add columns if they don't exist (Simple check, just try alter and ignore error or check pragma)
            // SQLite doesn't support IF NOT EXISTS in ALTER TABLE easily. 
            // We can try-catch the alter statements.
            try { stmt.execute("ALTER TABLE log_garage_ops ADD COLUMN operator_name VARCHAR(16)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE log_ownership_ops ADD COLUMN operator_name VARCHAR(16)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE log_usage_ops ADD COLUMN player_name VARCHAR(16)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE log_usage_ops ADD COLUMN operation_type VARCHAR(16)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE log_identity_changes ADD COLUMN player_name VARCHAR(16)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE garage_vehicles ADD COLUMN model_index INTEGER DEFAULT 1"); } catch (SQLException ignored) {}

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
