package com.github.cinnaio.transportation.database;

import com.github.cinnaio.transportation.scheduler.PlatformScheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class LogDAO {

    private final DatabaseManager dbManager;
    private final PlatformScheduler scheduler;

    public LogDAO(DatabaseManager dbManager, PlatformScheduler scheduler) {
        this.dbManager = dbManager;
        this.scheduler = scheduler;
    }

    public void logPurchase(UUID playerId, String playerName, String vehicleType, double amount, boolean success, String failReason) {
        String sql = "INSERT INTO log_purchases (player_id, player_name, vehicle_type, amount, success, fail_reason) VALUES (?, ?, ?, ?, ?, ?)";
        asyncExecute(sql, playerId.toString(), playerName, vehicleType, amount, success, failReason);
    }

    public void logDestruction(String vehicleIdentityCode, String location, String reason) {
        String sql = "INSERT INTO log_destruction (vehicle_identity_code, death_location, death_reason) VALUES (?, ?, ?)";
        asyncExecute(sql, vehicleIdentityCode, location, reason);
    }

    public void logGarageOp(UUID operatorUuid, String operatorName, String vehicleUuid, String operationType, boolean success, String failReason) {
        String sql = "INSERT INTO log_garage_ops (operator_uuid, operator_name, vehicle_uuid, operation_type, success, fail_reason) VALUES (?, ?, ?, ?, ?, ?)";
        asyncExecute(sql, operatorUuid.toString(), operatorName, vehicleUuid, operationType, success, failReason);
    }

    public void logOwnershipOp(UUID operatorId, String operatorName, String vehicleIdentityCode, String vehicleModel, String category, String content, boolean success, String failReason) {
        String sql = "INSERT INTO log_ownership_ops (operator_id, operator_name, vehicle_identity_code, vehicle_model, operation_category, operation_content, success, fail_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        asyncExecute(sql, operatorId.toString(), operatorName, vehicleIdentityCode, vehicleModel, category, content, success, failReason);
    }

    public void logUsageOp(UUID playerId, String playerName, String vehicleIdentityCode, String operationType, String location, boolean hasPermission) {
        String sql = "INSERT INTO log_usage_ops (player_id, player_name, vehicle_identity_code, operation_type, location, has_permission) VALUES (?, ?, ?, ?, ?, ?)";
        asyncExecute(sql, playerId.toString(), playerName, vehicleIdentityCode, operationType, location, hasPermission);
    }

    public void logIdentityChange(UUID playerId, String playerName, String oldCode, String newCode) {
        String sql = "INSERT INTO log_identity_changes (player_id, player_name, old_identity_code, new_identity_code) VALUES (?, ?, ?, ?)";
        asyncExecute(sql, playerId.toString(), playerName, oldCode, newCode);
    }

    private void asyncExecute(String sql, Object... args) {
        scheduler.runAsync(() -> {
            try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    pstmt.setObject(i + 1, args[i]);
                }
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
