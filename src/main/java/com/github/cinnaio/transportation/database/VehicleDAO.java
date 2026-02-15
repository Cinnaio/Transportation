package com.github.cinnaio.transportation.database;

import com.github.cinnaio.transportation.model.GarageVehicle;
import com.github.cinnaio.transportation.model.ServerVehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VehicleDAO {

    private final DatabaseManager dbManager;

    public VehicleDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // Garage Vehicle Operations
    public void addGarageVehicle(GarageVehicle vehicle) throws SQLException {
        String sql = "INSERT INTO garage_vehicles (owner_id, owner_name, vehicle_identity_code, vehicle_model, vehicle_model_id, model_index, stats_original, stats_extended, is_in_garage, is_frozen, is_destroyed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vehicle.getOwnerUuid().toString());
            pstmt.setString(2, vehicle.getOwnerName());
            pstmt.setString(3, vehicle.getIdentityCode());
            pstmt.setString(4, vehicle.getModel());
            pstmt.setString(5, vehicle.getModelId());
            pstmt.setInt(6, vehicle.getModelIndex());
            pstmt.setString(7, vehicle.getStatsOriginal());
            pstmt.setString(8, vehicle.getStatsExtended());
            pstmt.setBoolean(9, vehicle.isInGarage());
            pstmt.setBoolean(10, vehicle.isFrozen());
            pstmt.setBoolean(11, vehicle.isDestroyed());
            pstmt.executeUpdate();
        }
    }

    public GarageVehicle getGarageVehicle(String identityCode) throws SQLException {
        String sql = "SELECT * FROM garage_vehicles WHERE vehicle_identity_code = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, identityCode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToGarageVehicle(rs);
                }
            }
        }
        return null;
    }

    public List<GarageVehicle> getPlayerVehicles(UUID ownerUuid) throws SQLException {
        List<GarageVehicle> vehicles = new ArrayList<>();
        String sql = "SELECT * FROM garage_vehicles WHERE owner_id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    vehicles.add(mapResultSetToGarageVehicle(rs));
                }
            }
        }
        return vehicles;
    }

    public void updateGarageVehicle(GarageVehicle vehicle) throws SQLException {
        String sql = "UPDATE garage_vehicles SET owner_id = ?, owner_name = ?, vehicle_model = ?, vehicle_model_id = ?, model_index = ?, stats_original = ?, stats_extended = ?, is_in_garage = ?, is_frozen = ?, is_destroyed = ? WHERE vehicle_identity_code = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vehicle.getOwnerUuid().toString());
            pstmt.setString(2, vehicle.getOwnerName());
            pstmt.setString(3, vehicle.getModel());
            pstmt.setString(4, vehicle.getModelId());
            pstmt.setInt(5, vehicle.getModelIndex());
            pstmt.setString(6, vehicle.getStatsOriginal());
            pstmt.setString(7, vehicle.getStatsExtended());
            pstmt.setBoolean(8, vehicle.isInGarage());
            pstmt.setBoolean(9, vehicle.isFrozen());
            pstmt.setBoolean(10, vehicle.isDestroyed());
            pstmt.setString(11, vehicle.getIdentityCode());
            pstmt.executeUpdate();
        }
    }
    
    public void updateVehicleIdentityCode(String oldCode, String newCode) throws SQLException {
        String sql = "UPDATE garage_vehicles SET vehicle_identity_code = ? WHERE vehicle_identity_code = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newCode);
            pstmt.setString(2, oldCode);
            pstmt.executeUpdate();
        }
    }

    private GarageVehicle mapResultSetToGarageVehicle(ResultSet rs) throws SQLException {
        return new GarageVehicle(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("owner_name"),
                rs.getString("vehicle_identity_code"),
                rs.getString("vehicle_model"),
                rs.getString("vehicle_model_id"),
                rs.getInt("model_index"),
                rs.getString("stats_original"),
                rs.getString("stats_extended"),
                rs.getBoolean("is_in_garage"),
                rs.getBoolean("is_frozen"),
                rs.getBoolean("is_destroyed")
        );
    }

    public int getNextModelIndex(UUID ownerUuid, String model) throws SQLException {
        String sql = "SELECT COALESCE(MAX(model_index), 0) AS max_idx FROM garage_vehicles WHERE owner_id = ? AND vehicle_model = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ownerUuid.toString());
            pstmt.setString(2, model);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("max_idx") + 1;
                }
            }
        }
        return 1;
    }

    // Server Vehicle Operations
    public ServerVehicle getServerVehicle(int id) throws SQLException {
        String sql = "SELECT * FROM server_vehicles WHERE id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ServerVehicle(
                            rs.getInt("id"),
                            rs.getString("vehicle_name"),
                            rs.getString("vehicle_model"),
                            rs.getString("stats_original"),
                            rs.getString("stats_extended"),
                            rs.getDouble("price")
                    );
                }
            }
        }
        return null;
    }
    
    // Add methods to manage server vehicles (add, remove) if needed by admins
    
    public void reload() {
        dbManager.reload();
    }
}
