package com.github.cinnaio.transportation.manager;

import com.github.cinnaio.transportation.config.ConfigManager;
import com.github.cinnaio.transportation.config.LanguageManager;
import com.github.cinnaio.transportation.database.LogDAO;
import com.github.cinnaio.transportation.database.VehicleDAO;
import com.github.cinnaio.transportation.model.GarageVehicle;
import com.github.cinnaio.transportation.model.ServerVehicle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.AbstractHorse;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;

import java.util.Set;
import java.util.HashSet;

public class VehicleManager {

    private final VehicleDAO vehicleDAO;
    private final LogDAO logDAO;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    
    private final Map<UUID, Long> summonCooldowns = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeVehicleEntities = new ConcurrentHashMap<>();
    private final Map<String, Location> vehicleLocations = new ConcurrentHashMap<>();
    
    // Set of frozen vehicle UUIDs (runtime only)
    private final Set<UUID> frozenVehicles = new HashSet<>();

    private final JavaPlugin plugin;

    public VehicleManager(JavaPlugin plugin, VehicleDAO vehicleDAO, LogDAO logDAO, EconomyManager economyManager, ConfigManager configManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.vehicleDAO = vehicleDAO;
        this.logDAO = logDAO;
        this.economyManager = economyManager;
        this.configManager = configManager;
        this.languageManager = languageManager;
    }
    
    // Register active entity (called by listener when chunk loads or entity spawns)
    public void registerActiveEntity(String identityCode, UUID entityUuid) {
        activeVehicleEntities.put(identityCode, entityUuid);
    }
    
    // Unregister active entity
    public void unregisterActiveEntity(String identityCode) {
        activeVehicleEntities.remove(identityCode);
    }

    public void updateVehicleLocation(String identityCode, Location location) {
        if (location == null) {
            vehicleLocations.remove(identityCode);
        } else {
            vehicleLocations.put(identityCode, location);
        }
    }

    private Entity forceGetEntity(String identityCode) {
        UUID uuid = activeVehicleEntities.get(identityCode);
        if (uuid == null) return null;
        
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null) {
            Location loc = vehicleLocations.get(identityCode);
            if (loc != null) {
                loc.getChunk().load();
                entity = Bukkit.getEntity(uuid);
            }
        }
        return entity;
    }

    private void invalidateKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        
        // Check if already marked
        // Simple check: convert to string and check? Or just append.
        // To avoid duplicates, maybe we should check.
        // But since we use component, it's hard to check content without serialization.
        // We'll just append for now, or check if last line is the invalid marker.
        // Assuming user won't spam click 100 times.
        // Actually, if we update lore, the item changes, so next click it might not match?
        // No, identity code is still there.
        
        // Let's try to detect if last line is already invalid marker.
        // For simplicity, just append.
        
        lore.add(languageManager.getComponent("key-lore-invalid"));
        meta.lore(lore);
        // Reset display name to default
        meta.displayName(null);

        item.setItemMeta(meta);
    }

    private void invalidateKeyIfHeld(Player player, String identityCode) {
        ItemStack main = player.getInventory().getItemInMainHand();
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        if (checkItemKey(main, key, identityCode)) {
            invalidateKey(main);
        }
        
        ItemStack off = player.getInventory().getItemInOffHand();
        if (checkItemKey(off, key, identityCode)) {
            invalidateKey(off);
        }
    }

    public void buyVehicle(Player player, String modelName) {
        try {
            // Find server vehicle by name/model
            ServerVehicle serverVehicle = null;
            // Simple mock lookup for now, in real app should be DB or Cache map
            for(int i=1; i<=100; i++) {
                ServerVehicle v = vehicleDAO.getServerVehicle(i);
                if(v != null && (v.getName().equalsIgnoreCase(modelName) || v.getModel().equalsIgnoreCase(modelName))) {
                    serverVehicle = v;
                    break;
                }
            }

            if (serverVehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                logDAO.logPurchase(player.getUniqueId(), player.getName(), modelName, 0, false, "Model not found");
                return;
            }
            
            // Check Garage Limit
            List<GarageVehicle> playerVehicles = vehicleDAO.getPlayerVehicles(player.getUniqueId());
            if (playerVehicles.size() >= configManager.getDefaultGarageLimit()) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("garage-full"));
                 logDAO.logPurchase(player.getUniqueId(), player.getName(), modelName, 0, false, "Garage full");
                 return;
            }

            // Check Money
            double price = serverVehicle.getPrice();
            if (configManager.isEconomyEnabled()) {
                if (!economyManager.withdraw(player, price)) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("money-insufficient"));
                    logDAO.logPurchase(player.getUniqueId(), player.getName(), modelName, price, false, "Insufficient funds");
                    return;
                }
            }

            String identityCode = generateIdentityCode();
            GarageVehicle newVehicle = new GarageVehicle(
                    0, // ID auto-increment
                    player.getUniqueId(),
                    player.getName(),
                    identityCode,
                    serverVehicle.getName(),
                    serverVehicle.getModel(),
                    serverVehicle.getStatsOriginal(),
                    serverVehicle.getStatsExtended(),
                    true, // In garage by default
                    false,
                    false
            );

            vehicleDAO.addGarageVehicle(newVehicle);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("buy-success").replace("%price%", String.valueOf(price)));
            
            logDAO.logPurchase(player.getUniqueId(), player.getName(), serverVehicle.getName(), price, true, null);
            logDAO.logOwnershipOp(player.getUniqueId(), player.getName(), identityCode, serverVehicle.getName(), "BUY", "Purchased vehicle", true, null);

        } catch (SQLException e) {
            e.printStackTrace();
            logDAO.logPurchase(player.getUniqueId(), player.getName(), modelName, 0, false, "Database error: " + e.getMessage());
        }
    }

    public void summonVehicle(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = findVehicle(player, identityCode);
            boolean isKeyAccess = false;

            if (vehicle == null) {
                // Try global lookup for key access
                vehicle = vehicleDAO.getGarageVehicle(identityCode);
                if (vehicle != null) {
                    if (isHoldingKey(player, identityCode)) {
                        isKeyAccess = true;
                    } else {
                        // Found but not owner and no key
                        vehicle = null; 
                    }
                }
            } else {
                // Found in owned list
                if (!vehicle.getOwnerUuid().equals(player.getUniqueId())) {
                     // Should not happen given findVehicle logic, but safe to check
                     if (isHoldingKey(player, identityCode)) {
                         isKeyAccess = true;
                     }
                }
            }
            
            if (vehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }

            if (!vehicle.getOwnerUuid().equals(player.getUniqueId()) && !isKeyAccess) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("not-owner"));
                return;
            }
            
            if (vehicle.isFrozen() && !vehicle.getOwnerUuid().equals(player.getUniqueId())) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-frozen"));
                 return;
            }
            
            if (vehicle.isDestroyed()) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("destroyed"));
                 return;
            }

            if (!vehicle.isInGarage()) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("already-out"));
                return;
            }
            
            // Check Cooldown
            long now = System.currentTimeMillis();
            if (summonCooldowns.containsKey(player.getUniqueId())) {
                long lastSummon = summonCooldowns.get(player.getUniqueId());
                long cooldownMs = configManager.getSummonCooldown() * 1000L;
                if (now - lastSummon < cooldownMs) {
                    long remaining = (cooldownMs - (now - lastSummon)) / 1000;
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("cooldown").replace("%time%", String.valueOf(remaining)));
                    return;
                }
            }

            // Check for ghost entity (duplication fix)
            Entity ghost = forceGetEntity(vehicle.getIdentityCode());
            if (ghost != null) {
                ghost.remove();
                activeVehicleEntities.remove(vehicle.getIdentityCode());
            }

            // Spawn logic
            Location spawnLoc = getSafeSpawnLocation(player);
            EntityType type = EntityType.MINECART; // Fallback
            try {
                // Try to match model name to EntityType, else use default from config
                try {
                    type = EntityType.valueOf(vehicle.getModel().toUpperCase());
                } catch (IllegalArgumentException e) {
                    type = EntityType.valueOf(configManager.getDefaultVehicleType().toUpperCase());
                }
            } catch (IllegalArgumentException ignored) {
                // If config is also invalid, stay with MINECART
            }
            
            Entity entity = player.getWorld().spawnEntity(spawnLoc, type);
            entity.setCustomName(vehicle.getModel() + " (" + vehicle.getOwnerName() + ")");
            entity.setCustomNameVisible(true);
            
            // Add NBT tag
            NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
            PersistentDataContainer container = entity.getPersistentDataContainer();
            container.set(key, PersistentDataType.STRING, vehicle.getIdentityCode());
            
            // Register in memory
            activeVehicleEntities.put(vehicle.getIdentityCode(), entity.getUniqueId());

            // Apply saved stats
            if (vehicle.getStatsExtended() != null && !vehicle.getStatsExtended().isEmpty()) {
                applyEntityData(entity, vehicle.getStatsExtended());
            }

            // Ensure ownership is correct for Tameables
            if (entity instanceof Tameable) {
                Tameable tameable = (Tameable) entity;
                if (tameable.isTamed()) {
                    tameable.setOwner(Bukkit.getOfflinePlayer(vehicle.getOwnerUuid()));
                }
            }

            player.sendMessage(languageManager.get("prefix") + languageManager.get("summon-success"));
            summonCooldowns.put(player.getUniqueId(), now);

            vehicle.setInGarage(false);
            vehicleDAO.updateGarageVehicle(vehicle);

            logDAO.logGarageOp(player.getUniqueId(), player.getName(), vehicle.getIdentityCode(), "SUMMON", true, null);

        } catch (SQLException e) {
            e.printStackTrace();
            logDAO.logGarageOp(player.getUniqueId(), player.getName(), identityCode, "SUMMON", false, "Database error");
        }
    }

    public void recallVehicle(Player player, String identityCode) {
        try {
             GarageVehicle vehicle = findVehicle(player, identityCode);
             boolean isKeyAccess = false;

             if (vehicle == null) {
                 // Try global lookup for key access
                 vehicle = vehicleDAO.getGarageVehicle(identityCode);
                 if (vehicle != null) {
                     if (isHoldingKey(player, identityCode)) {
                         isKeyAccess = true;
                     } else {
                         vehicle = null;
                     }
                 }
             } else {
                 if (!vehicle.getOwnerUuid().equals(player.getUniqueId())) {
                     if (isHoldingKey(player, identityCode)) {
                         isKeyAccess = true;
                     }
                 }
             }

            if (vehicle == null) {
                 invalidateKeyIfHeld(player, identityCode);
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                 return;
            }
            
            if (!vehicle.getOwnerUuid().equals(player.getUniqueId()) && !isKeyAccess) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("not-owner"));
                return;
            }
            
            if (vehicle.isFrozen() && !vehicle.getOwnerUuid().equals(player.getUniqueId())) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-frozen"));
                 return;
            }

            if (vehicle.isInGarage()) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("already-in"));
                return;
            }

            // Despawn logic
            Entity entity = forceGetEntity(vehicle.getIdentityCode());
            boolean removed = false;
            
            if (entity != null) {
                // Save entity state before removing
                String serializedData = serializeEntityData(entity);
                vehicle.setStatsExtended(serializedData);
                
                entity.remove();
                removed = true;
            }
            
            // If not found in memory (e.g. restart), we might need to rely on listener or just warn
            if (!removed) {
                // Warning: Entity might still be in the world if it was unloaded or plugin restarted
                // However, we still mark it as in garage so player can summon it again (and we spawn a new one).
                // The old one will be "ghost" vehicle until we implement scanning or cleaning.
                player.sendMessage(languageManager.get("prefix") + languageManager.get("warning-entity-not-found"));
            }
            
            activeVehicleEntities.remove(vehicle.getIdentityCode());
            
            vehicle.setInGarage(true);
            vehicleDAO.updateGarageVehicle(vehicle);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("recall-success"));

            logDAO.logGarageOp(player.getUniqueId(), player.getName(), vehicle.getIdentityCode(), "RECALL", true, null);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void transferVehicle(Player sender, String identityCode, Player target) {
        try {
            GarageVehicle vehicle = findVehicle(sender, identityCode);
            if (vehicle == null) {
                sender.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }
            
            // Check target garage limit
            List<GarageVehicle> targetVehicles = vehicleDAO.getPlayerVehicles(target.getUniqueId());
            if (targetVehicles.size() >= configManager.getDefaultGarageLimit()) {
                sender.sendMessage(languageManager.get("prefix") + languageManager.get("target-garage-full"));
                return;
            }

            vehicle.setOwnerUuid(target.getUniqueId());
            vehicle.setOwnerName(target.getName());
            vehicleDAO.updateGarageVehicle(vehicle);

            // Update active entity custom name if it exists (to show new owner)
            UUID activeEntityUuid = activeVehicleEntities.get(identityCode);
            if (activeEntityUuid != null) {
                Entity entity = Bukkit.getEntity(activeEntityUuid);
                if (entity != null) {
                    entity.setCustomName(vehicle.getModel() + " (" + vehicle.getOwnerName() + ")");
                }
            }

            sender.sendMessage(languageManager.get("prefix") + languageManager.get("transfer-success").replace("%player%", target.getName()));
            target.sendMessage(languageManager.get("prefix") + languageManager.get("receive-vehicle").replace("%player%", sender.getName()));

            logDAO.logOwnershipOp(sender.getUniqueId(), sender.getName(), vehicle.getIdentityCode(), vehicle.getModel(), "TRANSFER", "Transferred to " + target.getName(), true, null);

        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage(languageManager.get("prefix") + languageManager.get("database-error"));
        }
    }
    
    public List<GarageVehicle> getPlayerVehicles(UUID ownerId) {
        try {
            return vehicleDAO.getPlayerVehicles(ownerId);
        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    public void toggleFreeze(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = vehicleDAO.getGarageVehicle(identityCode);
            if(vehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }
            
            if (!vehicle.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("transportation.admin")) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("not-owner"));
                return;
            }
            
            boolean newState = !vehicle.isFrozen();
            vehicle.setFrozen(newState);
            vehicleDAO.updateGarageVehicle(vehicle);
            
            player.sendMessage(languageManager.get("prefix") + languageManager.get("freeze-success").replace("%status%", String.valueOf(newState)));
             logDAO.logOwnershipOp(player.getUniqueId(), player.getName(), vehicle.getIdentityCode(), vehicle.getModel(), newState ? "FREEZE" : "UNFREEZE", "", true, null);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void reviveVehicle(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = findVehicle(player, identityCode);
            
            if (vehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }
            
            if (!vehicle.isDestroyed()) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("fix-not-destroyed"));
                return;
            }
            
            double cost = configManager.getFixCost();
            if (configManager.isEconomyEnabled()) {
                if (!economyManager.withdraw(player, cost)) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("money-insufficient"));
                    return;
                }
            }
            
            vehicle.setDestroyed(false);
            vehicleDAO.updateGarageVehicle(vehicle);
            
            player.sendMessage(languageManager.get("prefix") + languageManager.get("fix-success").replace("%price%", String.valueOf(cost)));
            logDAO.logGarageOp(player.getUniqueId(), player.getName(), vehicle.getIdentityCode(), "FIX", true, "Cost: " + cost);
            
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(languageManager.get("prefix") + languageManager.get("database-error"));
        }
    }

    public void rekeyVehicle(Player player, String inputId) {
        GarageVehicle vehicle = null;
        try {
            vehicle = findVehicle(player, inputId);
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(languageManager.get("prefix") + languageManager.get("database-error"));
            return;
        }

        if (vehicle == null) {
            // Try to find by model name if inputId is not a valid identity code
            // This is a simple fallback, iterating owned vehicles
            try {
                List<GarageVehicle> owned = vehicleDAO.getPlayerVehicles(player.getUniqueId());
                for (GarageVehicle v : owned) {
                    if (v.getModel().equalsIgnoreCase(inputId)) {
                        vehicle = v;
                        break;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        if (vehicle == null) {
            player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
            return;
        }

        String oldIdentityCode = vehicle.getIdentityCode();
        String newCode = generateIdentityCode();
        
        try {
            vehicleDAO.updateVehicleIdentityCode(oldIdentityCode, newCode);
            logDAO.logIdentityChange(player.getUniqueId(), player.getName(), oldIdentityCode, newCode);
            
            // Update Active Entity if exists
            UUID entityUuid = activeVehicleEntities.remove(oldIdentityCode);
            Entity entity = null;

            if (entityUuid != null) {
                entity = Bukkit.getEntity(entityUuid);
            }
            
            // Fallback: Search nearby entities if not found in map
            if (entity == null) {
                NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
                for (Entity nearby : player.getNearbyEntities(50, 50, 50)) {
                    PersistentDataContainer container = nearby.getPersistentDataContainer();
                    if (container.has(key, PersistentDataType.STRING)) {
                        String code = container.get(key, PersistentDataType.STRING);
                        if (code.equals(oldIdentityCode)) {
                            entity = nearby;
                            break;
                        }
                    }
                }
            }

            if (entity != null) {
                // Eject passengers to force re-validation
                for (Entity passenger : entity.getPassengers()) {
                    entity.removePassenger(passenger);
                }

                NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
                entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, newCode);
                activeVehicleEntities.put(newCode, entity.getUniqueId());
            }
            
            // Scan online players to invalidate old keys
            NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack item = p.getInventory().getItem(i);
                    if (item == null || !item.hasItemMeta()) continue;
                    
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    if (container.has(key, PersistentDataType.STRING)) {
                        String code = container.get(key, PersistentDataType.STRING);
                         if (code.equals(oldIdentityCode)) {
                             // Found a key with old code
                             invalidateKey(item);
                             // Explicitly set item back to inventory to ensure update
                             p.getInventory().setItem(i, item);
                         }
                     }
                 }
             }

            player.sendMessage(languageManager.get("prefix") + languageManager.get("rekey-success") + languageManager.get("new-code").replace("%code%", newCode));
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(languageManager.get("prefix") + languageManager.get("rekey-error"));
        }
    }
    
    public boolean canDrive(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = vehicleDAO.getGarageVehicle(identityCode);
            if (vehicle == null) return false;
            
            // Owner can always drive
            if (player.getUniqueId().equals(vehicle.getOwnerUuid())) return true;
            
            if (vehicle.isFrozen()) return false;
            
            // Otherwise, must hold key
            return isHoldingKey(player, identityCode);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void freezeVehicle(UUID vehicleUuid) {
        frozenVehicles.add(vehicleUuid);
    }
    
    public void unfreezeVehicle(UUID vehicleUuid) {
        frozenVehicles.remove(vehicleUuid);
    }
    
    public boolean isFrozen(UUID vehicleUuid) {
        return frozenVehicles.contains(vehicleUuid);
    }

    public void restrictVehicleMovement(Entity entity) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) entity;
        
        Attribute speedAttrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed"));
        if (speedAttrType == null) return;

        AttributeInstance speedAttr = livingEntity.getAttribute(speedAttrType);
        if (speedAttr == null) return;
        
        // Save current base value if not already saved
        PersistentDataContainer container = entity.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "transportation_saved_speed");
        if (!container.has(key, PersistentDataType.DOUBLE)) {
            container.set(key, PersistentDataType.DOUBLE, speedAttr.getBaseValue());
        }
        
        speedAttr.setBaseValue(0.0);
    }
    
    public void restoreVehicleMovement(Entity entity) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) entity;
        
        Attribute speedAttrType = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed"));
        if (speedAttrType == null) return;
        
        AttributeInstance speedAttr = livingEntity.getAttribute(speedAttrType);
        if (speedAttr == null) return;
        
        PersistentDataContainer container = entity.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "transportation_saved_speed");
        
        if (container.has(key, PersistentDataType.DOUBLE)) {
            double originalSpeed = container.get(key, PersistentDataType.DOUBLE);
            speedAttr.setBaseValue(originalSpeed);
            container.remove(key);
        } else {
             // Fallback to a reasonable default if no saved data (e.g. Horse default ~0.225)
             // But usually if no saved data, it means it wasn't restricted, or it's a fresh load.
             // If current value is 0.0, and we have no saved data, we should probably try to restore a default.
             if (speedAttr.getBaseValue() == 0.0) {
                 speedAttr.setBaseValue(0.225); // Generic default
             }
        }
    }

    private boolean isHoldingKey(Player player, String identityCode) {
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        
        // Check main hand
        if (checkItemKey(player.getInventory().getItemInMainHand(), key, identityCode)) return true;
        
        // Check off hand
        if (checkItemKey(player.getInventory().getItemInOffHand(), key, identityCode)) return true;
        
        // Check entire inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (checkItemKey(item, key, identityCode)) return true;
        }
        
        return false;
    }
    
    private boolean checkItemKey(ItemStack item, NamespacedKey key, String identityCode) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.STRING)) return false;
        String keyId = container.get(key, PersistentDataType.STRING);
        return identityCode.equals(keyId);
    }

    public void createBoundVehicle(Player player, String modelName, Entity entity) {
        // Used for /vehicle bind
         try {
            // Check allowed entity types
            List<String> allowedTypes = configManager.getAllowedEntities();
            if (allowedTypes != null && !allowedTypes.isEmpty()) {
                if (!allowedTypes.contains(entity.getType().name())) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("bind-invalid-type").replace("%types%", String.join(", ", allowedTypes)));
                    return;
                }
            }

            // Check if already bound
            NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
            PersistentDataContainer container = entity.getPersistentDataContainer();
            if (container.has(key, PersistentDataType.STRING)) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("bind-already-bound"));
                 return;
            }

            List<GarageVehicle> playerVehicles = vehicleDAO.getPlayerVehicles(player.getUniqueId());
            if (playerVehicles.size() >= configManager.getDefaultGarageLimit()) {
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("garage-full"));
                 return;
            }
            
            String identityCode = generateIdentityCode();
            
            // Apply NBT
            container.set(key, PersistentDataType.STRING, identityCode);
            
            // Set Custom Name immediately
            entity.setCustomName(modelName + " (" + player.getName() + ")");
            entity.setCustomNameVisible(true);
            
            // Register in memory
            activeVehicleEntities.put(identityCode, entity.getUniqueId());
            
             GarageVehicle newVehicle = new GarageVehicle(
                    0, 
                    player.getUniqueId(),
                    player.getName(),
                    identityCode,
                    modelName,
                    "custom",
                    "{}",
                    "{}",
                    false, // It's in the world (we are looking at it)
                    false,
                    false
            );
            
            vehicleDAO.addGarageVehicle(newVehicle);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("bind-success"));
            logDAO.logOwnershipOp(player.getUniqueId(), player.getName(), identityCode, modelName, "BIND", "Bound existing entity", true, null);

        } catch (SQLException e) {
             e.printStackTrace();
         }
    }
    
    public void unbindVehicle(Player player, String identityCode) {
         try {
            GarageVehicle vehicle = findVehicle(player, identityCode);
            if(vehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }
            
            // Clean up entity visuals and NBT if currently loaded
            UUID entityUuid = activeVehicleEntities.get(identityCode);
            if (entityUuid != null) {
                Entity entity = Bukkit.getEntity(entityUuid);
                if (entity != null) {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                    entity.getPersistentDataContainer().remove(new NamespacedKey(plugin, "vehicle_identity_code"));
                }
                activeVehicleEntities.remove(identityCode);
            }

            // "Release" the vehicle by setting owner to a null-like UUID
             vehicleDAO.updateGarageVehicle(new GarageVehicle(
                vehicle.getId(),
                UUID.fromString("00000000-0000-0000-0000-000000000000"), // Null UUID
                "Unowned",
                vehicle.getIdentityCode(),
                vehicle.getModel(),
                vehicle.getModelId(),
                vehicle.getStatsOriginal(),
                vehicle.getStatsExtended(),
                false,
                false,
                false
            ));
            
            player.sendMessage(languageManager.get("prefix") + languageManager.get("unbind-success"));
            logDAO.logOwnershipOp(player.getUniqueId(), player.getName(), identityCode, vehicle.getModel(), "UNBIND", "Unbound vehicle", true, null);

        } catch (SQLException e) {
             e.printStackTrace();
         }
    }

    public void handleVehicleDestruction(String identityCode, Location location, String reason) {
        try {
            GarageVehicle vehicle = vehicleDAO.getGarageVehicle(identityCode);
            if (vehicle != null) {
                vehicle.setDestroyed(true);
                vehicle.setInGarage(true); 
                vehicleDAO.updateGarageVehicle(vehicle);
                
                String locStr = location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
                logDAO.logDestruction(identityCode, locStr, reason);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Helper to find vehicle by ID or Model Name (First match)
    private GarageVehicle findVehicle(Player player, String arg) throws SQLException {
        GarageVehicle vehicle = vehicleDAO.getGarageVehicle(arg);
        if (vehicle != null && vehicle.getOwnerUuid().equals(player.getUniqueId())) {
            return vehicle;
        }
        
        List<GarageVehicle> owned = vehicleDAO.getPlayerVehicles(player.getUniqueId());
        for(GarageVehicle v : owned) {
            if(v.getIdentityCode().equalsIgnoreCase(arg)) return v;
        }
        for(GarageVehicle v : owned) {
            if(v.getModel().equalsIgnoreCase(arg)) return v;
        }
        return null;
    }

    private String generateIdentityCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void unbindVehicleKey(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
             player.sendMessage(languageManager.get("prefix") + languageManager.get("bind-hand-empty"));
             return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        
        if (!container.has(key, PersistentDataType.STRING)) {
             player.sendMessage(languageManager.get("prefix") + "§cThis item is not a bound key.");
             return;
        }
        
        // Remove data
        container.remove(key);
        
        // Remove lore (assuming last line is ours, or clear all? Safe to remove last if it matches format?)
        // Since we can't easily identify which line is ours without parsing, and user might have added other lore.
        // We will try to remove the line that looks like our key format.
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            // Remove the last line as it's most likely the one we added
            lore.remove(lore.size() - 1);
            meta.lore(lore);
        }
        
        // Reset display name if it was set to the default key name? 
        // User didn't ask to reset name, but maybe we should?
        // "Also in tra keybind... modify item name". User didn't say reset it on unbind.
        // But usually unbinding makes it a normal item. 
        // I'll leave the name as is or maybe reset it to material name? 
        // I'll just leave it to avoid deleting custom names if the user renamed it before binding.
        // Wait, binding overwrites name. Unbinding should probably not leave it as "坐骑召唤道具".
        // But the user didn't specify. I'll just remove the binding data.

        // Reset display name to default
        meta.displayName(null);

        item.setItemMeta(meta);
        player.sendMessage(languageManager.get("prefix") + languageManager.get("key-unbind-success"));
    }

    public void bindVehicleToItem(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = findVehicle(player, identityCode);
            
            if (vehicle == null) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-not-found"));
                return;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                 player.sendMessage(languageManager.get("prefix") + "§cYou must hold an item to bind.");
                 return;
            }

            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
            container.set(key, PersistentDataType.STRING, vehicle.getIdentityCode());
            
            // Add lore
            List<net.kyori.adventure.text.Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            
            String loreFormat = languageManager.getRaw("key-lore-format");
            if (loreFormat == null) loreFormat = "<color:#AAAAAA>Bound Vehicle: <color:#FFAA00>%type% %id% <color:#AAAAAA>(<color:#FFFFFF>Owner %owner%<color:#AAAAAA>)";
            
            // Do not replace placeholders with values that might contain legacy color codes if we are using MiniMessage
            // But here we just assume values are safe plain text, or we strip colors.
            // The issue is MiniMessage parser sees something it thinks is legacy formatting.
            // It's likely the user configured messages.yml or values contain § or & which is not allowed in MiniMessage strict mode?
            // Actually, the error says "Legacy formatting codes have been detected".
            // We should use a deserializer that handles this, or just strip legacy codes from inputs.
            
            // Simplest fix: The error comes from parsing the string.
            // If we are constructing the string manually, we should ensure no legacy codes are present in the *format* string passed to parse().
            // But placeholders might contain them.
            
            // Better approach: use Component template/args if LanguageManager supported it, but it seems to take raw string.
            // So we'll use a legacy serializer to convert everything to Component, OR
            // we will strip colors from the inputs (model, id, owner) before injecting into MiniMessage string.
            
            String safeModel = org.bukkit.ChatColor.stripColor(vehicle.getModel());
            String safeId = org.bukkit.ChatColor.stripColor(vehicle.getIdentityCode());
            String safeOwner = org.bukkit.ChatColor.stripColor(vehicle.getOwnerName());
            
            loreFormat = loreFormat.replace("%type%", safeModel)
                                   .replace("%id%", safeId)
                                   .replace("%owner%", safeOwner);
            
            // Also ensure the format string itself doesn't contain & or § if that's what triggers it.
            // But likely it was the replaced values or just existing config.
            
            lore.add(languageManager.parse(loreFormat));
            meta.lore(lore);

            // Set display name to &d坐骑召唤道具 (Light Purple)
            meta.displayName(languageManager.parse("&d坐骑召唤道具"));

            item.setItemMeta(meta);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("key-bound"));
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void bindVehicleToItem(Player player, Entity entity) {
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.STRING)) {
            player.sendMessage(languageManager.get("prefix") + languageManager.get("not-vehicle"));
             return;
        }
        String identityCode = container.get(key, PersistentDataType.STRING);
        bindVehicleToItem(player, identityCode);
    }
    
    public void reload() {
        vehicleDAO.reload();
    }
    
    private Location getSafeSpawnLocation(Player player) {
        Location origin = player.getLocation();
        // Target 2 blocks in front, or config distance
        int distance = (int) Math.min(2, configManager.getSpawnDistance()); 
        if (distance < 1) distance = 1;
        
        Location target = origin.clone().add(origin.getDirection().multiply(distance));
        
        // Ensure integer coordinates for block checks
        target.setX(target.getBlockX() + 0.5);
        target.setY(target.getBlockY());
        target.setZ(target.getBlockZ() + 0.5);

        // 1. Check if target is safe
        if (isSafeLocation(target)) {
            return target;
        }

        // 2. Check 3x3 area around target at same Y
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                Location test = target.clone().add(x, 0, z);
                if (isSafeLocation(test)) {
                    return test;
                }
            }
        }
        
        // 3. Check up 1 block at target
        Location up = target.clone().add(0, 1, 0);
        if (isSafeLocation(up)) return up;

        // 4. Fallback: Player location (slightly offset to avoid clipping directly into player)
        // Usually player location is safe.
        return origin;
    }

    private boolean isSafeLocation(Location loc) {
        org.bukkit.block.Block block = loc.getBlock();
        org.bukkit.block.Block above = loc.clone().add(0, 1, 0).getBlock();
        return !block.getType().isSolid() && !above.getType().isSolid();
    }

    private String serializeEntityData(Entity entity) {
        JsonObject json = new JsonObject();
        
        // Custom Name
        if (entity.getCustomName() != null) {
            json.addProperty("customName", entity.getCustomName());
            json.addProperty("customNameVisible", entity.isCustomNameVisible());
        }

        // LivingEntity Attributes
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            json.addProperty("health", living.getHealth());
            
            Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
            if (type != null) {
                AttributeInstance maxHealth = living.getAttribute(type);
                if (maxHealth != null) json.addProperty("maxHealth", maxHealth.getBaseValue());
            }
            
            type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed"));
            if (type != null) {
                AttributeInstance speed = living.getAttribute(type);
                if (speed != null) json.addProperty("speed", speed.getBaseValue());
            }
        }

        // Tameable
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            json.addProperty("tamed", tameable.isTamed());
        }
        
        // AbstractHorse (Saddle)
        if (entity instanceof AbstractHorse) {
            AbstractHorse abstractHorse = (AbstractHorse) entity;
            ItemStack saddle = abstractHorse.getInventory().getSaddle();
            if (saddle != null) {
                json.addProperty("saddle", itemStackToBase64(saddle));
            }
        }
        
        // Horse
        if (entity instanceof Horse) {
            Horse horse = (Horse) entity;
            json.addProperty("color", horse.getColor().name());
            json.addProperty("style", horse.getStyle().name());
            
            ItemStack armor = horse.getInventory().getArmor();
            if (armor != null) {
                json.addProperty("armor", itemStackToBase64(armor));
            }
            
            Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
            if (type != null) {
                AttributeInstance jump = horse.getAttribute(type);
                if (jump != null) json.addProperty("jumpStrength", jump.getBaseValue());
            }
        }
        
        // Llama
        if (entity instanceof Llama) {
            Llama llama = (Llama) entity;
            json.addProperty("color", llama.getColor().name());
            json.addProperty("strength", llama.getStrength());
            
            ItemStack decor = llama.getInventory().getDecor();
            if (decor != null) {
                json.addProperty("decor", itemStackToBase64(decor));
            }
            
            Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
            if (type != null) {
                AttributeInstance jump = llama.getAttribute(type);
                if (jump != null) json.addProperty("jumpStrength", jump.getBaseValue());
            }
        }
        
        // ChestedHorse (Donkey, Mule, Llama)
        if (entity instanceof ChestedHorse) {
            ChestedHorse chested = (ChestedHorse) entity;
            json.addProperty("hasChest", chested.isCarryingChest());
        }
        
        return json.toString();
    }
    
    private void applyEntityData(Entity entity, String jsonStr) {
        try {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            
            // LivingEntity Attributes
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                
                if (json.has("maxHealth")) {
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
                    if (type != null) {
                        AttributeInstance maxHealth = living.getAttribute(type);
                        if (maxHealth != null) maxHealth.setBaseValue(json.get("maxHealth").getAsDouble());
                    }
                }
                
                if (json.has("health")) {
                    double health = json.get("health").getAsDouble();
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
                    if (type != null) {
                        double max = living.getAttribute(type).getValue();
                        living.setHealth(Math.min(health, max));
                    }
                }
                
                if (json.has("speed")) {
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed"));
                    if (type != null) {
                        AttributeInstance speed = living.getAttribute(type);
                        if (speed != null) speed.setBaseValue(json.get("speed").getAsDouble());
                    }
                }
            }
            
            // Custom Name
            if (json.has("customName")) {
                 String name = json.get("customName").getAsString();
                 entity.setCustomName(name);
                 if (json.has("customNameVisible")) {
                     entity.setCustomNameVisible(json.get("customNameVisible").getAsBoolean());
                 }
            }
            
            // Tameable
            if (entity instanceof Tameable) {
                Tameable tameable = (Tameable) entity;
                if (json.has("tamed") && json.get("tamed").getAsBoolean()) {
                    tameable.setTamed(true);
                }
            }
            
            // AbstractHorse (Saddle)
            if (entity instanceof AbstractHorse) {
                AbstractHorse abstractHorse = (AbstractHorse) entity;
                if (json.has("saddle")) {
                    abstractHorse.getInventory().setSaddle(itemStackFromBase64(json.get("saddle").getAsString()));
                }
            }

            // Horse
            if (entity instanceof Horse) {
                Horse horse = (Horse) entity;
                if (json.has("color")) horse.setColor(Horse.Color.valueOf(json.get("color").getAsString()));
                if (json.has("style")) horse.setStyle(Horse.Style.valueOf(json.get("style").getAsString()));
                if (json.has("armor")) horse.getInventory().setArmor(itemStackFromBase64(json.get("armor").getAsString()));
                if (json.has("jumpStrength")) {
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
                    if (type != null) {
                        AttributeInstance jump = horse.getAttribute(type);
                        if (jump != null) jump.setBaseValue(json.get("jumpStrength").getAsDouble());
                    }
                }
            }
            
            // Llama
            if (entity instanceof Llama) {
                Llama llama = (Llama) entity;
                if (json.has("color")) llama.setColor(Llama.Color.valueOf(json.get("color").getAsString()));
                if (json.has("strength")) llama.setStrength(json.get("strength").getAsInt());
                if (json.has("decor")) llama.getInventory().setDecor(itemStackFromBase64(json.get("decor").getAsString()));
                if (json.has("jumpStrength")) {
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
                    if (type != null) {
                        AttributeInstance jump = llama.getAttribute(type);
                        if (jump != null) jump.setBaseValue(json.get("jumpStrength").getAsDouble());
                    }
                }
            }
            
            // ChestedHorse
            if (entity instanceof ChestedHorse) {
                ChestedHorse chested = (ChestedHorse) entity;
                if (json.has("hasChest") && json.get("hasChest").getAsBoolean()) {
                    chested.setCarryingChest(true);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String itemStackToBase64(ItemStack item) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stack.", e);
        }
    }

    private ItemStack itemStackFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
