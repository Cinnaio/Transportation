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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private final boolean isFolia;
    private static final java.util.UUID NULL_UUID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
    private final java.util.Map<String, Long> lastSnapshots = new java.util.concurrent.ConcurrentHashMap<>();

    public VehicleManager(JavaPlugin plugin, VehicleDAO vehicleDAO, LogDAO logDAO, EconomyManager economyManager, ConfigManager configManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.vehicleDAO = vehicleDAO;
        this.logDAO = logDAO;
        this.economyManager = economyManager;
        this.configManager = configManager;
        this.languageManager = languageManager;
        
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            folia = true;
        } catch (Exception e) {}
        this.isFolia = folia;
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
    
    private String decodeStatsString(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        char c = stored.charAt(0);
        if (c == '{' || c == '[') return stored;
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(stored);
            java.io.InputStream base = new java.io.ByteArrayInputStream(decoded);
            java.io.InputStream in;
            if (decoded.length >= 2 && decoded[0] == (byte) 0x1f && decoded[1] == (byte) 0x8b) {
                in = new java.util.zip.GZIPInputStream(base);
            } else {
                in = base;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return stored;
        }
    }

    private String encodeStatsJson(String json) {
        if (json == null || json.isEmpty()) return json;
        try {
            byte[] raw = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos)) {
                gzip.write(raw);
            }
            byte[] compressed = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            return json;
        }
    }

    public com.google.gson.JsonObject getStatsSnapshot(com.github.cinnaio.transportation.model.GarageVehicle v) {
        try {
            String stats = v.getStatsExtended();
            if (stats != null && !stats.isEmpty()) {
                String json = decodeStatsString(stats);
                return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            }
            Entity e = forceGetEntity(v.getIdentityCode());
            if (e != null) {
                String json = serializeEntityData(e);
                com.google.gson.JsonObject jo = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                String encoded = encodeStatsJson(json);
                runAsync(() -> {
                    try {
                        v.setStatsExtended(encoded);
                        vehicleDAO.updateGarageVehicle(v);
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                });
                return jo;
            }
        } catch (Exception ignored) {}
        return new com.google.gson.JsonObject();
    }

    public com.google.gson.JsonObject readStatsExtended(com.github.cinnaio.transportation.model.GarageVehicle v) {
        try {
            String stats = v.getStatsExtended();
            if (stats == null || stats.isEmpty()) return new com.google.gson.JsonObject();
            String json = decodeStatsString(stats);
            return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new com.google.gson.JsonObject();
        }
    }
    
    public String buildStatusHover(com.github.cinnaio.transportation.model.GarageVehicle v) {
        if (v.isDestroyed()) {
            return "已损毁\n使用 /tra fix 可尝试修复";
        }
        if (v.isFrozen()) {
            return "已冻结\n无法移动，使用 /tra freeze 解除";
        }
        if (v.isInGarage()) {
            return "已存放至兽栏";
        }
        Location loc = vehicleLocations.get(v.getIdentityCode());
        if (loc != null && loc.getWorld() != null) {
            return "位置: " + loc.getWorld().getName() + "\n坐标: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        }
        return "位置未知";
    }

    private void invalidateKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey invalidKey = new NamespacedKey(plugin, "vehicle_key_invalid");
        if (pdc.has(invalidKey, PersistentDataType.BYTE)) {
            item.setItemMeta(meta);
            return;
        }
        pdc.set(invalidKey, PersistentDataType.BYTE, (byte)1);
        
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        List<net.kyori.adventure.text.Component> newLore = new ArrayList<>();
        if (lore != null) {
            for (net.kyori.adventure.text.Component c : lore) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
                if (plain == null || !plain.contains("已失效")) {
                    newLore.add(c);
                }
            }
        }
        newLore.add(languageManager.getComponent("key-lore-invalid"));
        meta.lore(newLore);
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
            int nextIndex = vehicleDAO.getNextModelIndex(player.getUniqueId(), serverVehicle.getName());
            GarageVehicle newVehicle = new GarageVehicle(
                    0, // ID auto-increment
                    player.getUniqueId(),
                    player.getName(),
                    identityCode,
                    serverVehicle.getName(),
                    serverVehicle.getModel(),
                    nextIndex,
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

            // Block summoning unowned vehicles even with a key
            if (vehicle.getOwnerUuid() != null && vehicle.getOwnerUuid().equals(NULL_UUID)) {
                invalidateKeyIfHeld(player, identityCode);
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-unbound"));
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

            if (vehicle.getStatsExtended() != null && !vehicle.getStatsExtended().isEmpty()) {
                String json = decodeStatsString(vehicle.getStatsExtended());
                applyEntityData(entity, json);
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
            
            // Block recalling unowned vehicles even with a key
            if (vehicle.getOwnerUuid() != null && vehicle.getOwnerUuid().equals(NULL_UUID)) {
                invalidateKeyIfHeld(player, identityCode);
                player.sendMessage(languageManager.get("prefix") + languageManager.get("vehicle-unbound"));
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
            
            if (entity != null) {
                final GarageVehicle finalVehicle = vehicle;
                runOnEntity(entity, () -> {
                    String serializedData = serializeEntityData(entity);
                    finalVehicle.setStatsExtended(encodeStatsJson(serializedData));
                    entity.remove();
                    
                    runOnGlobal(() -> finalizeRecall(player, finalVehicle, true));
                });
                return;
            }
            
            // Fallback: Scan all worlds (Skip on Folia to avoid unsafe access)
            boolean removed = false;
            if (!isFolia) {
                NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    for (Entity e : w.getEntities()) {
                        PersistentDataContainer container = e.getPersistentDataContainer();
                        if (container.has(key, PersistentDataType.STRING)) {
                            String code = container.get(key, PersistentDataType.STRING);
                            if (code.equals(vehicle.getIdentityCode())) {
                                String serializedData = serializeEntityData(e);
                                vehicle.setStatsExtended(encodeStatsJson(serializedData));
                                e.remove();
                                removed = true;
                                break;
                            }
                        }
                    }
                    if (removed) break;
                }
            }
            
            finalizeRecall(player, vehicle, removed);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void finalizeRecall(Player player, GarageVehicle vehicle, boolean removed) {
        if (!removed) {
            player.sendMessage(languageManager.get("prefix") + languageManager.get("warning-entity-not-found"));
        }
        
        activeVehicleEntities.remove(vehicle.getIdentityCode());
        
        vehicle.setInGarage(true);
        try {
            vehicleDAO.updateGarageVehicle(vehicle);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("recall-success"));
            logDAO.logGarageOp(player.getUniqueId(), player.getName(), vehicle.getIdentityCode(), "RECALL", true, null);
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(languageManager.get("prefix") + languageManager.get("database-error"));
        }
    }

    private void runOnEntity(Entity entity, Runnable task) {
        if (isFolia) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            task.run();
        }
    }

    private void runOnGlobal(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    private void runAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
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

            // If entity is currently active in world, persist its latest stats before transferring
            UUID activeEntityUuid = activeVehicleEntities.get(identityCode);
            if (activeEntityUuid != null) {
                Entity entity = Bukkit.getEntity(activeEntityUuid);
                if (entity != null) {
                    String serializedData = serializeEntityData(entity);
                    vehicle.setStatsExtended(encodeStatsJson(serializedData));
                }
            }

            vehicle.setOwnerUuid(target.getUniqueId());
            vehicle.setOwnerName(target.getName());
            vehicle.setModelIndex(vehicleDAO.getNextModelIndex(target.getUniqueId(), vehicle.getModel()));
            vehicleDAO.updateGarageVehicle(vehicle);

            // Update active entity custom name if it exists (to show new owner)
            if (activeEntityUuid != null) {
                Entity entity = Bukkit.getEntity(activeEntityUuid);
                if (entity != null) {
                    entity.setCustomName(vehicle.getModel() + " (" + vehicle.getOwnerName() + ")");
                    if (entity instanceof org.bukkit.entity.Tameable tameable) {
                        if (tameable.isTamed()) {
                            tameable.setOwner(Bukkit.getOfflinePlayer(target.getUniqueId()));
                        }
                    }
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
            GarageVehicle vehicle = findVehicle(player, identityCode);
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
            
            if (newState) {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("freeze-enabled"));
            } else {
                player.sendMessage(languageManager.get("prefix") + languageManager.get("freeze-disabled"));
            }
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
    
    public boolean isVehicleOwner(Player player, String identityCode) {
        try {
            GarageVehicle vehicle = vehicleDAO.getGarageVehicle(identityCode);
            if (vehicle == null) return false;
            return player.getUniqueId().equals(vehicle.getOwnerUuid());
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
        snapshotEntityAsync(entity);
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
        snapshotEntityAsync(entity);
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

    private void removeKeyBinding(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        if (container.has(key, PersistentDataType.STRING)) {
            container.remove(key);
        }
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            lore.remove(lore.size() - 1);
            meta.lore(lore);
        }
        meta.displayName(null);
        item.setItemMeta(meta);
    }

    private void invalidateAllKeysForIdentity(String identityCode) {
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item == null || !item.hasItemMeta()) continue;
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                if (container.has(key, PersistentDataType.STRING)) {
                    String code = container.get(key, PersistentDataType.STRING);
                    if (identityCode.equals(code)) {
                        removeKeyBinding(item);
                        p.getInventory().setItem(i, item);
                    }
                }
            }
        }
    }

    public void createBoundVehicle(Player player, String modelName, String modelId, Entity entity) {
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
            
            activeVehicleEntities.put(identityCode, entity.getUniqueId());
            
            String snapshot = serializeEntityData(entity);
            String encodedSnapshot = encodeStatsJson(snapshot);
            
             GarageVehicle newVehicle = new GarageVehicle(
                    0, 
                    player.getUniqueId(),
                    player.getName(),
                    identityCode,
                    modelName,
                    modelId,
                    vehicleDAO.getNextModelIndex(player.getUniqueId(), modelName),
                    "{}",
                    encodedSnapshot,
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
            String realCode = vehicle.getIdentityCode();
            UUID entityUuid = activeVehicleEntities.get(realCode);

            if (entityUuid != null) {
                Entity entity = Bukkit.getEntity(entityUuid);
                if (entity != null) {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                    entity.getPersistentDataContainer().remove(new NamespacedKey(plugin, "vehicle_identity_code"));
                    if (entity instanceof org.bukkit.entity.Tameable tameable) {
                        if (tameable.isTamed()) {
                            tameable.setTamed(false);
                            tameable.setOwner(null);
                        }
                    }
                }
                activeVehicleEntities.remove(realCode);
            }
            
            // Fallback: scan worlds to clear residual identity/name if active map missed it
            if (!isFolia) {
                NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    for (Entity e : w.getEntities()) {
                        PersistentDataContainer container = e.getPersistentDataContainer();
                        if (container.has(key, PersistentDataType.STRING)) {
                            String code = container.get(key, PersistentDataType.STRING);
                            if (code.equals(realCode)) {
                                e.setCustomName(null);
                                e.setCustomNameVisible(false);
                                container.remove(key);
                                if (e instanceof org.bukkit.entity.Tameable t) {
                                    if (t.isTamed()) {
                                        t.setTamed(false);
                                        t.setOwner(null);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Release" the vehicle by setting owner to a null-like UUID
             vehicleDAO.updateGarageVehicle(new GarageVehicle(
                vehicle.getId(),
                NULL_UUID, // Null UUID
                "Unowned",
                realCode,
                vehicle.getModel(),
                vehicle.getModelId(),
                vehicle.getModelIndex(),
                vehicle.getStatsOriginal(),
                vehicle.getStatsExtended(),
                false,
                false,
                false
            ));
            
            // Invalidate and remove all keys bound to this identity code from online players
            invalidateAllKeysForIdentity(realCode);

            player.sendMessage(languageManager.get("prefix") + languageManager.get("unbind-success"));
            logDAO.logOwnershipOp(player.getUniqueId(), player.getName(), realCode, vehicle.getModel(), "UNBIND", "Unbound vehicle", true, null);

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
    
    public void handleChunkUnloadEntity(String identityCode, Entity entity) {
        try {
            GarageVehicle vehicle = vehicleDAO.getGarageVehicle(identityCode);
            if (vehicle != null) {
                String serializedData = serializeEntityData(entity);
                vehicle.setStatsExtended(encodeStatsJson(serializedData));
                vehicle.setInGarage(true);
                vehicleDAO.updateGarageVehicle(vehicle);
            }
            entity.remove();
            activeVehicleEntities.remove(identityCode);
            updateVehicleLocation(identityCode, null);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Helper to find vehicle by ID or Model Name (First match)
    private GarageVehicle findVehicle(Player player, String arg) throws SQLException {
        if (arg != null && arg.contains("#")) {
            String[] parts = arg.split("#", 2);
            if (parts.length == 2) {
                String model = parts[0];
                int index = 1;
                try { index = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                List<GarageVehicle> owned = vehicleDAO.getPlayerVehicles(player.getUniqueId());
                for (GarageVehicle v : owned) {
                    if (v.getModel().equalsIgnoreCase(model) && v.getModelIndex() == index) {
                        return v;
                    }
                }
            }
        }
        if (arg != null && arg.contains("[") && arg.endsWith("]")) {
            int i = arg.indexOf("[");
            String model = arg.substring(0, i);
            String cname = arg.substring(i + 1, arg.length() - 1);
            List<GarageVehicle> owned = vehicleDAO.getPlayerVehicles(player.getUniqueId());
            for (GarageVehicle v : owned) {
                if (v.getModel().equalsIgnoreCase(model)) {
                    String stats = v.getStatsExtended();
                    if (stats != null && !stats.isEmpty()) {
                        try {
                            String json = decodeStatsString(stats);
                            com.google.gson.JsonObject jo = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                            if (jo.has("customName") && cname.equalsIgnoreCase(jo.get("customName").getAsString())) {
                                return v;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        if (arg != null && arg.contains(":")) {
            String[] parts = arg.split(":", 2);
            String m = parts[0];
            String mid = parts.length > 1 ? parts[1] : "";
            List<GarageVehicle> owned = vehicleDAO.getPlayerVehicles(player.getUniqueId());
            for (GarageVehicle v : owned) {
                if (v.getModel().equalsIgnoreCase(m) && v.getModelId().equalsIgnoreCase(mid)) {
                    return v;
                }
            }
            // Fallback: try identityCode equals the right part
            for (GarageVehicle v : owned) {
                if (v.getIdentityCode().equalsIgnoreCase(mid)) {
                    return v;
                }
            }
        }
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
             player.sendMessage(languageManager.get("prefix") + languageManager.get("key-hand-empty"));
             return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        
        if (!container.has(key, PersistentDataType.STRING)) {
             player.sendMessage(languageManager.get("prefix") + languageManager.get("key-not-bound"));
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
                 player.sendMessage(languageManager.get("prefix") + languageManager.get("bind-hand-empty"));
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
            
            // Speed (Fix: Check if restricted/saved in PDC)
            double finalSpeed = -1.0;
            NamespacedKey savedSpeedKey = new NamespacedKey(plugin, "transportation_saved_speed");
            if (entity.getPersistentDataContainer().has(savedSpeedKey, PersistentDataType.DOUBLE)) {
                finalSpeed = entity.getPersistentDataContainer().get(savedSpeedKey, PersistentDataType.DOUBLE);
            }
            
            type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed"));
            if (type != null) {
                AttributeInstance speed = living.getAttribute(type);
                if (speed != null) {
                    if (finalSpeed < 0) finalSpeed = speed.getBaseValue();
                    json.addProperty("speed", finalSpeed);
                }
            }

            // Potion Effects
            java.util.Collection<PotionEffect> effects = living.getActivePotionEffects();
            if (!effects.isEmpty()) {
                com.google.gson.JsonArray effectsJson = new com.google.gson.JsonArray();
                for (PotionEffect effect : effects) {
                    JsonObject effectObj = new JsonObject();
                    effectObj.addProperty("type", effect.getType().getKey().toString());
                    effectObj.addProperty("duration", effect.getDuration());
                    effectObj.addProperty("amplifier", effect.getAmplifier());
                    effectObj.addProperty("ambient", effect.isAmbient());
                    effectObj.addProperty("particles", effect.hasParticles());
                    effectObj.addProperty("icon", effect.hasIcon());
                    effectsJson.add(effectObj);
                }
                json.add("potionEffects", effectsJson);
            }
            
            // Capture all attribute base values
            com.google.gson.JsonObject attrs = new com.google.gson.JsonObject();
            for (org.bukkit.attribute.Attribute attrEnum : org.bukkit.attribute.Attribute.values()) {
                org.bukkit.attribute.AttributeInstance inst = living.getAttribute(attrEnum);
                if (inst != null && attrEnum.getKey() != null) {
                    attrs.addProperty(attrEnum.getKey().toString(), inst.getBaseValue());
                }
            }
            if (attrs.size() > 0) {
                json.add("attributes", attrs);
            }
            
            // Equipment (armor/saddle and generic equipment)
            org.bukkit.inventory.EntityEquipment eq = living.getEquipment();
            if (eq != null) {
                ItemStack main = eq.getItemInMainHand();
                ItemStack off = eq.getItemInOffHand();
                if (main != null && !main.getType().isAir()) json.addProperty("equipMain", itemStackToBase64(main));
                if (off != null && !off.getType().isAir()) json.addProperty("equipOff", itemStackToBase64(off));
            }
        }

        // Tameable
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            json.addProperty("tamed", tameable.isTamed());
        }
        
        // AbstractHorse (Saddle + Jump Strength)
        if (entity instanceof AbstractHorse) {
            AbstractHorse abstractHorse = (AbstractHorse) entity;
            ItemStack saddle = abstractHorse.getInventory().getSaddle();
            if (saddle != null) {
                json.addProperty("saddle", itemStackToBase64(saddle));
            }
            
            // Jump Strength (Try generic first, then horse legacy)
            Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
            if (type == null) {
                type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("horse.jump_strength"));
            }
            
            if (type != null) {
                AttributeInstance jump = abstractHorse.getAttribute(type);
                if (jump != null) json.addProperty("jumpStrength", jump.getBaseValue());
            }
            
            // Inventory contents (包括箱子内物品)
            org.bukkit.inventory.Inventory inv = abstractHorse.getInventory();
            if (inv != null && inv.getSize() > 0) {
                com.google.gson.JsonArray items = new com.google.gson.JsonArray();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it != null && !it.getType().isAir()) {
                        JsonObject slot = new JsonObject();
                        slot.addProperty("slot", i);
                        slot.addProperty("item", itemStackToBase64(it));
                        items.add(slot);
                    }
                }
                if (items.size() > 0) {
                    json.add("inventory", items);
                }
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

                // Potion Effects
                if (json.has("potionEffects")) {
                    com.google.gson.JsonArray effectsJson = json.getAsJsonArray("potionEffects");
                    for (com.google.gson.JsonElement element : effectsJson) {
                        JsonObject effectObj = element.getAsJsonObject();
                        String typeKey = effectObj.get("type").getAsString();
                        org.bukkit.potion.PotionEffectType pType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(NamespacedKey.fromString(typeKey));
                        
                        if (pType != null) {
                            int duration = effectObj.get("duration").getAsInt();
                            int amplifier = effectObj.get("amplifier").getAsInt();
                            boolean ambient = effectObj.get("ambient").getAsBoolean();
                            boolean particles = effectObj.get("particles").getAsBoolean();
                            boolean icon = effectObj.get("icon").getAsBoolean();
                            
                            living.addPotionEffect(new org.bukkit.potion.PotionEffect(pType, duration, amplifier, ambient, particles, icon));
                        }
                    }
                }
                
                // Restore all attribute base values
                if (json.has("attributes")) {
                    JsonObject attrs = json.getAsJsonObject("attributes");
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : attrs.entrySet()) {
                        String keyStr = entry.getKey();
                        double baseVal = entry.getValue().getAsDouble();
                        org.bukkit.attribute.Attribute attrType = Registry.ATTRIBUTE.get(NamespacedKey.fromString(keyStr));
                        if (attrType != null) {
                            AttributeInstance inst = living.getAttribute(attrType);
                            if (inst != null) {
                                inst.setBaseValue(baseVal);
                            }
                        }
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
            
            // AbstractHorse (Saddle + Jump Strength)
            if (entity instanceof AbstractHorse) {
                AbstractHorse abstractHorse = (AbstractHorse) entity;
                if (json.has("saddle")) {
                    abstractHorse.getInventory().setSaddle(itemStackFromBase64(json.get("saddle").getAsString()));
                }
                if (json.has("jumpStrength")) {
                    Attribute type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.jump_strength"));
                    if (type == null) {
                        type = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("horse.jump_strength"));
                    }
                    if (type != null) {
                        AttributeInstance jump = abstractHorse.getAttribute(type);
                        if (jump != null) jump.setBaseValue(json.get("jumpStrength").getAsDouble());
                    }
                }
            }

            // Horse
            if (entity instanceof Horse) {
                Horse horse = (Horse) entity;
                if (json.has("color")) horse.setColor(Horse.Color.valueOf(json.get("color").getAsString()));
                if (json.has("style")) horse.setStyle(Horse.Style.valueOf(json.get("style").getAsString()));
                if (json.has("armor")) horse.getInventory().setArmor(itemStackFromBase64(json.get("armor").getAsString()));
            }
            
            // Llama
            if (entity instanceof Llama) {
                Llama llama = (Llama) entity;
                if (json.has("color")) llama.setColor(Llama.Color.valueOf(json.get("color").getAsString()));
                if (json.has("strength")) llama.setStrength(json.get("strength").getAsInt());
                if (json.has("decor")) llama.getInventory().setDecor(itemStackFromBase64(json.get("decor").getAsString()));
            }
            
            // ChestedHorse
            if (entity instanceof ChestedHorse) {
                ChestedHorse chested = (ChestedHorse) entity;
                if (json.has("hasChest") && json.get("hasChest").getAsBoolean()) {
                    chested.setCarryingChest(true);
                }
            }
            
            // Restore inventory contents (包括箱子内物品)
            if (entity instanceof AbstractHorse && json.has("inventory")) {
                AbstractHorse ah = (AbstractHorse) entity;
                org.bukkit.inventory.Inventory inv = ah.getInventory();
                com.google.gson.JsonArray items = json.getAsJsonArray("inventory");
                for (com.google.gson.JsonElement el : items) {
                    JsonObject slot = el.getAsJsonObject();
                    int idx = slot.get("slot").getAsInt();
                    ItemStack it = itemStackFromBase64(slot.get("item").getAsString());
                    if (idx >= 0 && idx < inv.getSize()) {
                        inv.setItem(idx, it);
                    }
                }
            }
            
            // Equipment restore
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                org.bukkit.inventory.EntityEquipment eq = living.getEquipment();
                if (eq != null) {
                    if (json.has("equipMain")) eq.setItemInMainHand(itemStackFromBase64(json.get("equipMain").getAsString()));
                    if (json.has("equipOff")) eq.setItemInOffHand(itemStackFromBase64(json.get("equipOff").getAsString()));
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void snapshotEntityAsync(Entity entity) {
        NamespacedKey key = new NamespacedKey(plugin, "vehicle_identity_code");
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.STRING)) return;
        String identityCode = pdc.get(key, PersistentDataType.STRING);
        long now = System.currentTimeMillis();
        Long last = lastSnapshots.get(identityCode);
        if (last != null && (now - last) < 500) {
            return; // debounce
        }
        lastSnapshots.put(identityCode, now);
        
        runOnEntity(entity, () -> {
            String json = serializeEntityData(entity);
            String encoded = encodeStatsJson(json);
            runAsync(() -> {
                try {
                    GarageVehicle v = vehicleDAO.getGarageVehicle(identityCode);
                    if (v != null) {
                        v.setStatsExtended(encoded);
                        vehicleDAO.updateGarageVehicle(v);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    private String itemStackToBase64(ItemStack item) throws IllegalStateException {
        try {
            ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(rawOut);
            dataOutput.writeObject(item);
            dataOutput.close();
            byte[] raw = rawOut.toByteArray();

            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressedOut)) {
                gzip.write(raw);
            }
            byte[] compressed = compressedOut.toByteArray();
            return Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stack.", e);
        }
    }

    private ItemStack itemStackFromBase64(String data) throws IOException {
        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            java.io.InputStream base = new ByteArrayInputStream(decoded);

            // Support both legacy (raw) and new (GZIP) encodings
            java.io.InputStream in;
            if (decoded.length >= 2 && (decoded[0] == (byte) 0x1f && decoded[1] == (byte) 0x8b)) {
                in = new java.util.zip.GZIPInputStream(base);
            } else {
                in = base;
            }

            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(in);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
