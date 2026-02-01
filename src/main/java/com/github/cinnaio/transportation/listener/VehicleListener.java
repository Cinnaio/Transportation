package com.github.cinnaio.transportation.listener;

import com.github.cinnaio.transportation.manager.VehicleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.entity.Entity;
import org.bukkit.Bukkit;

import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerMoveEvent;

import com.github.cinnaio.transportation.config.LanguageManager;

public class VehicleListener implements Listener {

    private final VehicleManager vehicleManager;
    private final LanguageManager languageManager;
    private final JavaPlugin plugin;
    private final NamespacedKey keyId;

    public VehicleListener(JavaPlugin plugin, VehicleManager vehicleManager, LanguageManager languageManager, com.github.cinnaio.transportation.database.LogDAO logDAO) {
        this.plugin = plugin;
        this.vehicleManager = vehicleManager;
        this.languageManager = languageManager;
        this.logDAO = logDAO;
        this.keyId = new NamespacedKey(plugin, "vehicle_identity_code");
    }

    private final com.github.cinnaio.transportation.database.LogDAO logDAO;
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isInsideVehicle()) return;
        
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        
        // Check if vehicle is frozen by our manager
        if (vehicleManager.isFrozen(vehicle.getUniqueId())) {
             event.setCancelled(true);
             vehicle.setVelocity(new Vector(0, 0, 0));
        }
    }
    
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        org.bukkit.entity.Vehicle vehicle = event.getVehicle();
        if (vehicleManager.isFrozen(vehicle.getUniqueId())) {
            vehicle.setVelocity(new Vector(0, 0, 0));
        }
    }
    
    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        Player player = (Player) event.getEntered();
        Entity vehicle = event.getVehicle();
        
        // Check if vehicle is managed by this plugin
        if (!vehicle.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) return;
        
        String identityCode = vehicle.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        
        // Check permissions
        boolean hasPermission = vehicleManager.canDrive(player, identityCode);
        
        // Log usage (Enter)
        String locationStr = vehicle.getLocation().getWorld().getName() + "," + vehicle.getLocation().getBlockX() + "," + vehicle.getLocation().getBlockY() + "," + vehicle.getLocation().getBlockZ();
        logDAO.logUsageOp(player.getUniqueId(), player.getName(), identityCode, "ENTER", locationStr, hasPermission);
        
        if (hasPermission) {
            // Has permission, ensure no restrictions
            vehicleManager.restoreVehicleMovement(vehicle);
        } else {
            // No permission, cancel entry entirely
            event.setCancelled(true);
            player.sendMessage(languageManager.get("prefix") + languageManager.get("no-key-access"));
        }
    }
    
    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        Entity vehicle = event.getVehicle();
        
        // Check if vehicle is managed by this plugin
        if (!vehicle.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) return;
        
        // Log usage (Exit) if player
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            String identityCode = vehicle.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
            String locationStr = vehicle.getLocation().getWorld().getName() + "," + vehicle.getLocation().getBlockX() + "," + vehicle.getLocation().getBlockY() + "," + vehicle.getLocation().getBlockZ();
            logDAO.logUsageOp(player.getUniqueId(), player.getName(), identityCode, "EXIT", locationStr, true);
        }
        
        // Restore movement when anyone exits (to be safe/clean state)
        // Also remove from frozen list
        vehicleManager.restoreVehicleMovement(vehicle);
        vehicleManager.unfreezeVehicle(vehicle.getUniqueId());
    }
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
                String code = entity.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                vehicleManager.registerActiveEntity(code, entity.getUniqueId());
                vehicleManager.updateVehicleLocation(code, null);
            }
        }
    }
    
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
                String code = entity.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                vehicleManager.updateVehicleLocation(code, entity.getLocation());
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
            // Prevent drops to avoid item duplication (items are restored on revive)
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            String code = entity.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
            vehicleManager.handleVehicleDestruction(code, entity.getLocation(), "Death");
            vehicleManager.unregisterActiveEntity(code);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (!isVehicleKey(item)) {
            return;
        }
        
        String keyIdentity = getIdentityFromKey(item);
        if (keyIdentity == null) return;
        
        Entity entity = event.getRightClicked();
        PersistentDataContainer container = entity.getPersistentDataContainer();
        
        // If entity is not a vehicle, do nothing (let other plugins handle it)
        if (!container.has(keyId, PersistentDataType.STRING)) return;
        
        String entityIdentity = container.get(keyId, PersistentDataType.STRING);
        
        // Only proceed if key matches entity
        if (keyIdentity.equals(entityIdentity)) {
            event.setCancelled(true);
            vehicleManager.recallVehicle(player, keyIdentity);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Check if item is a vehicle key
        if (!isVehicleKey(item)) {
            return;
        }

        // If clicking a block that might be an entity interaction handled by onPlayerInteractEntity,
        // we should be careful not to double fire if both events trigger.
        // However, onPlayerInteract fires for blocks/air. onPlayerInteractEntity fires for entities.
        // They are distinct.
        // BUT: if user right clicks AIR, they get "vehicle not found" from summonVehicle if key is invalid.
        // if user right clicks BLOCK, they get "vehicle not found" from summonVehicle if key is invalid.
        
        // The issue: "vehicle not found" appears twice.
        // Likely because both MainHand and OffHand trigger the event.
        
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return; 
        }

        String identityCode = getIdentityFromKey(item);
        if (identityCode == null) return;

        event.setCancelled(true); // Prevent placing block if key is a block

        if (player.isSneaking()) {
            // Shift + Right Click -> Recall (In)
            vehicleManager.recallVehicle(player, identityCode);
        } else {
            // Right Click -> Summon (Out)
            vehicleManager.summonVehicle(player, identityCode);
        }
    }

    private boolean isVehicleKey(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(keyId, PersistentDataType.STRING);
    }

    private String getIdentityFromKey(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(keyId, PersistentDataType.STRING);
    }
}
