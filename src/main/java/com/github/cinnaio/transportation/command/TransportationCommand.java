package com.github.cinnaio.transportation.command;

import com.github.cinnaio.transportation.config.ConfigManager;
import com.github.cinnaio.transportation.config.LanguageManager;
import com.github.cinnaio.transportation.manager.VehicleManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransportationCommand implements TabExecutor {

    private final VehicleManager vehicleManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public TransportationCommand(VehicleManager vehicleManager, LanguageManager languageManager, ConfigManager configManager) {
        this.vehicleManager = vehicleManager;
        this.languageManager = languageManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.get("prefix") + languageManager.get("player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "buy":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.buyVehicle(player, args[1]);
                break;

            case "bind":
                // Raytrace to find entity
                RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), configManager.getRaytraceDistance(), entity -> !entity.getUniqueId().equals(player.getUniqueId()));
                if (result != null && result.getHitEntity() != null) {
                    Entity entity = result.getHitEntity();
                    // Check if entity has owner (this is tricky without metadata, assuming raw entities are unowned unless tagged)
                    // For now, assume if it's not in our DB, it's bindable.
                    // Ideally we check NBT or metadata.
                    // For this task, simply proceed to create a vehicle entry.
                    // Use entity type or name as model.
                    String model = entity.getCustomName() != null ? entity.getCustomName() : entity.getType().name();
                    vehicleManager.createBoundVehicle(player, model, entity);
                } else {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("no-entity-sight"));
                }
                break;

            case "unbind":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.unbindVehicle(player, args[1]);
                break;

            case "transfer":
                if (args.length < 3) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("player-not-found"));
                    return true;
                }
                vehicleManager.transferVehicle(player, args[1], target);
                break;

            case "freeze":
            case "unfreeze":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.toggleFreeze(player, args[1]);
                break;

            case "out":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.summonVehicle(player, args[1]);
                break;

            case "in":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.recallVehicle(player, args[1]);
                break;

            case "rekey":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.rekeyVehicle(player, args[1]);
                break;

            case "fix":
                if (args.length < 2) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("invalid-args"));
                    return true;
                }
                vehicleManager.reviveVehicle(player, args[1]);
                break;

            case "reload":
                if (!player.hasPermission("transportation.admin")) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("no-permission"));
                    return true;
                }
                configManager.reload();
                languageManager.reload();
                vehicleManager.reload();
                player.sendMessage(languageManager.get("prefix") + languageManager.get("reload-success"));
                break;

            case "list":
                List<com.github.cinnaio.transportation.model.GarageVehicle> vehicles = vehicleManager.getPlayerVehicles(player.getUniqueId());
                if (vehicles.isEmpty()) {
                    player.sendMessage(languageManager.get("prefix") + languageManager.get("no-vehicles"));
                    return true;
                }
                player.sendMessage(languageManager.get("list-header"));
                for (com.github.cinnaio.transportation.model.GarageVehicle v : vehicles) {
                    String status = v.isDestroyed() ? languageManager.get("status-destroyed") : (v.isFrozen() ? languageManager.get("status-frozen") : (v.isInGarage() ? languageManager.get("status-in-garage") : languageManager.get("status-out")));
                    player.sendMessage(languageManager.get("list-separator"));
                    player.sendMessage(languageManager.get("list-id") + v.getId());
                    player.sendMessage(languageManager.get("list-owner-id") + v.getOwnerUuid());
                    player.sendMessage(languageManager.get("list-owner-name") + v.getOwnerName());
                    player.sendMessage(languageManager.get("list-identity") + v.getIdentityCode());
                    player.sendMessage(languageManager.get("list-model") + v.getModel() + languageManager.get("list-model-suffix-id").replace("%id%", String.valueOf(v.getModelId())));
                    player.sendMessage(languageManager.get("list-stats-original") + v.getStatsOriginal());
                    player.sendMessage(languageManager.get("list-stats-extended") + v.getStatsExtended());
                    player.sendMessage(languageManager.get("list-in-garage") + v.isInGarage());
                    player.sendMessage(languageManager.get("list-frozen") + v.isFrozen());
                    player.sendMessage(languageManager.get("list-destroyed") + v.isDestroyed());
                    player.sendMessage(languageManager.get("list-status") + status);
                }
                player.sendMessage(languageManager.get("list-separator"));
                break;
                
            case "keybind":
                // Bind physical key item to vehicle identity
                if (args.length > 1) {
                     // bind by ID
                     vehicleManager.bindVehicleToItem(player, args[1]);
                } else {
                     // bind by looking at entity
                     RayTraceResult keybindResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), configManager.getRaytraceDistance(), entity -> !entity.getUniqueId().equals(player.getUniqueId()));
                     if (keybindResult != null && keybindResult.getHitEntity() != null) {
                         vehicleManager.bindVehicleToItem(player, keybindResult.getHitEntity());
                     } else {
                         player.sendMessage(languageManager.get("prefix") + languageManager.get("no-vehicle-sight"));
                     }
                }
                break;
                
            case "unkeybind":
                // Unbind physical key
                vehicleManager.unbindVehicleKey(player);
                break;

            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        List<String> helpMessages = Arrays.asList(
            "",
            "&6Transportation 1.0.0",
            "",
            " &c/tra buy &f<model&f>",
            " &7  └ 购买指定载具",
            " &c/tra out &f<id/model&f>",
            " &7  └ 召唤载具到当前位置",
            " &c/tra in &f<id/model&f>",
            " &7  └ 回收当前载具",
            " &c/tra freeze &f<id/model&f>",
            " &7  └ 冻结 / 解冻载具",
            " &c/tra bind",
            " &7  └ 绑定准心指向的载具",
            " &c/tra unbind &f<id/model&f>",
            " &7  └ 解绑或丢弃载具",
            " &c/tra transfer &f<id/model&f> &f<player&f>",
            " &7  └ 转让载具给其他玩家",
            " &c/tra rekey &f<id/model&f>",
            " &7  └ 重置载具身份码",
            " &c/tra keybind &f[id]",
            " &7  └ 将载具绑定到手中物品",
            " &c/tra unkeybind",
            " &7  └ 解除手中物品的绑定",
            " &c/tra list",
            " &7  └ 查看你拥有的载具列表",
            " &c/tra fix &f<id/model>",
            " &7  └ 修复/复活已损毁的载具",
            " &c/tra reload",
            " &7  └ 重载插件配置"
        );
        
        for (String msg : helpMessages) {
            player.sendMessage(languageManager.parse(msg));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("buy", "bind", "unbind", "transfer", "out", "in", "freeze", "unfreeze", "rekey", "help", "list", "reload", "keybind", "unkeybind", "fix");
            return filter(subCommands, args[0]);
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("buy")) {
                // Return list of server vehicles
                return Arrays.asList("Car", "Bike"); // Mock list
            }
            if (Arrays.asList("unbind", "transfer", "out", "in", "freeze", "unfreeze", "rekey", "keybind", "fix").contains(sub)) {
                List<String> identities = new ArrayList<>();
                List<com.github.cinnaio.transportation.model.GarageVehicle> vehicles = vehicleManager.getPlayerVehicles(((Player)sender).getUniqueId());
                for (com.github.cinnaio.transportation.model.GarageVehicle v : vehicles) {
                    identities.add(v.getIdentityCode());
                }
                return filter(identities, args[1]);
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("transfer")) {
            return null; // Player list
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        if (input == null || input.isEmpty()) return list;
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}
