package com.github.cinnaio.transportation.config;

import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public int getDefaultGarageLimit() {
        return plugin.getConfig().getInt("garage.default-limit", 5);
    }

    public int getSummonCooldown() {
        return plugin.getConfig().getInt("garage.summon-cooldown-seconds", 60);
    }

    public double getSpawnDistance() {
        return plugin.getConfig().getDouble("garage.spawn-distance", 2.0);
    }

    public String getDefaultVehicleType() {
        return plugin.getConfig().getString("garage.default-vehicle-type", "MINECART");
    }

    public double getRaytraceDistance() {
        return plugin.getConfig().getDouble("interaction.raytrace-distance", 5.0);
    }

    public java.util.List<String> getAllowedEntities() {
        return plugin.getConfig().getStringList("interaction.allowed-entities");
    }

    public boolean isEconomyEnabled() {
        return plugin.getConfig().getBoolean("economy.enabled", true);
    }
    
    public double getFixCost() {
        return plugin.getConfig().getDouble("economy.fix-cost", 100.0);
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
