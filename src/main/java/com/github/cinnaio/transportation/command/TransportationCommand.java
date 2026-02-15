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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

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
                    
                    String modelName;
                    String modelId = "vanilla";

                    if (args.length >= 2) {
                        String input = args[1];
                        if (input.contains(":")) {
                            String[] parts = input.split(":", 2);
                            modelName = parts[0];
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                modelId = parts[1];
                            }
                        } else {
                            modelName = input;
                        }
                    } else {
                        // Use entity type or name as model
                        modelName = entity.getCustomName() != null ? entity.getCustomName() : entity.getType().name();
                    }

                    vehicleManager.createBoundVehicle(player, modelName, modelId, entity);
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
                    String statusText = v.isDestroyed() ? "&c损毁" : (v.isFrozen() ? "&b冻结" : (v.isInGarage() ? "&6兽栏" : "&a在外"));
                    String statusHover = vehicleManager.buildStatusHover(v);
                    String main = "&7 ▸ &f种类: &e" + v.getModel() + "#" + v.getModelIndex() + "  " + buildHover("状态", statusText, statusHover);
                    
                    JsonObject jo = vehicleManager.readStatsExtended(v);
                    String detail = "";

                    try {
                        JsonObject attrs = jo.has("attributes") ? jo.getAsJsonObject("attributes") : null;

                        double speed = -1D;
                        if (jo.has("speed")) {
                            speed = jo.get("speed").getAsDouble();
                        } else if (attrs != null) {
                            if (attrs.has("minecraft:movement_speed")) {
                                speed = attrs.get("minecraft:movement_speed").getAsDouble();
                            } else if (attrs.has("minecraft:generic.movement_speed")) {
                                speed = attrs.get("minecraft:generic.movement_speed").getAsDouble();
                            }
                        }

                        double jump = -1D;
                        if (jo.has("jumpStrength")) {
                            jump = jo.get("jumpStrength").getAsDouble();
                        } else if (attrs != null && attrs.has("minecraft:jump_strength")) {
                            jump = attrs.get("minecraft:jump_strength").getAsDouble();
                        }

                        double health = jo.has("health") ? jo.get("health").getAsDouble() : -1D;
                        double maxHealth = -1D;
                        if (jo.has("maxHealth")) {
                            maxHealth = jo.get("maxHealth").getAsDouble();
                        } else if (attrs != null && attrs.has("minecraft:max_health")) {
                            maxHealth = attrs.get("minecraft:max_health").getAsDouble();
                        }
                        int potionCount = jo.has("potionEffects") ? jo.getAsJsonArray("potionEffects").size() : 0;
                        boolean tamed = jo.has("tamed") && jo.get("tamed").getAsBoolean();
                        boolean hasChest = jo.has("hasChest") && jo.get("hasChest").getAsBoolean();
                        boolean hasSaddle = jo.has("saddle");
                        
                        String speedText = mapSpeed(speed);
                        String jumpText = mapJump(jump);
                        String hpText = (health >= 0 && maxHealth > 0) ? ("&e" + (int)Math.round(health) + "/" + (int)Math.round(maxHealth)) : "&7未知";
                        String potionText = potionCount > 0 ? ("&e" + potionCount + "种") : "&7无";
                        
                        String speedHover = speed >= 0 ? ("数值: " + formatDouble(speed)) : "未检测到速度";
                        String jumpHover = jump >= 0 ? ("数值: " + formatDouble(jump)) : "未检测到跳跃";
                        String hpHover = (health >= 0 && maxHealth > 0) ? ("当前: " + formatDouble(health) + "\n最大: " + formatDouble(maxHealth)) : "未检测到生命值";
                        String potionHover = buildPotionHover(jo);
                        String tameHover = tamed ? "已驯服" : "未驯服";
                        String chestHover = hasChest ? "背负箱子" : "未背负箱子";
                        String saddleHover = hasSaddle ? "已装备鞍" : "未装备鞍";
                        
                        String segSpeed = buildHover("速度", "&b" + speedText, speedHover);
                        String segJump = buildHover("跳跃", "&b" + jumpText, jumpHover);
                        String segHp = buildHover("生命", hpText, hpHover);
                        String segPot = buildHover("药水", potionText, potionHover);
                        String segTame = buildHover("驯服", mapBool(tamed), tameHover);
                        String segChest = buildHover("箱子", mapBool(hasChest), chestHover);
                        String segSaddle = buildHover("鞍", mapBool(hasSaddle), saddleHover);
                        
                        detail = "&7    " + segSpeed + "  " + segJump + "  " + segHp + "  " + segPot + "  " + segTame + "  " + segChest + "  " + segSaddle;
                    } catch (Exception ignored) {}
                    
                    player.sendMessage(languageManager.parse(main));
                    if (!detail.isEmpty()) {
                        player.sendMessage(languageManager.parse(detail));
                    }
                }
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
            " &c/tra out &f<model&f>",
            " &7  └ 召唤载具到当前位置",
            " &c/tra in &f<model&f>",
            " &7  └ 回收当前载具",
            " &c/tra freeze &f<model&f>",
            " &7  └ 冻结 / 解冻载具",
            " &c/tra bind &f[model:id]",
            " &7  └ 绑定准心指向的载具 (默认 vanilla)",
            " &c/tra unbind &f<model&f>",
            " &7  └ 解绑或丢弃载具",
            " &c/tra transfer &f<model&f> &f<player&f>",
            " &7  └ 转让载具给其他玩家",
            " &c/tra rekey &f<model&f>",
            " &7  └ 重置载具身份码",
            " &c/tra keybind &f[model]",
            " &7  └ 将载具绑定到手中物品",
            " &c/tra unkeybind",
            " &7  └ 解除手中物品的绑定",
            " &c/tra list",
            " &7  └ 查看你拥有的载具列表",
            " &c/tra fix &f<model>",
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
            if (sub.equals("bind")) {
                // Return vehicle models
                List<String> models = new ArrayList<>();
                for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
                    if (type.isAlive() && type.isSpawnable()) {
                        models.add(type.name().toLowerCase());
                    }
                }
                return filter(models, args[1]);
            }
            if (Arrays.asList("unbind", "transfer", "out", "in", "freeze", "unfreeze", "rekey", "keybind", "fix").contains(sub)) {
                java.util.Set<String> options = new java.util.LinkedHashSet<>();
                List<com.github.cinnaio.transportation.model.GarageVehicle> vehicles = vehicleManager.getPlayerVehicles(((Player)sender).getUniqueId());
                for (com.github.cinnaio.transportation.model.GarageVehicle v : vehicles) {
                    String model = v.getModel();
                    options.add(model + "#" + v.getModelIndex());
                }
                return filter(new java.util.ArrayList<>(options), args[1]);
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
    
    private String mapBool(boolean b) {
        return b ? "&a是" : "&c否";
    }
    
    private String buildHover(String label, String value, String hoverText) {
        String safe = hoverText == null ? "" : hoverText.replace("'", "\\'");
        return "&f" + label + ": <hover:show_text:'" + safe + "'>" + value + "</hover>";
    }
    
    private String buildPotionHover(JsonObject jo) {
        if (!jo.has("potionEffects")) return "无药水";
        JsonArray arr = jo.getAsJsonArray("potionEffects");
        if (arr.size() == 0) return "无药水";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject e = arr.get(i).getAsJsonObject();
            String typeKey = e.get("type").getAsString();
            String shortName = typeKey.contains(":") ? typeKey.substring(typeKey.indexOf(":") + 1) : typeKey;
            int dur = e.get("duration").getAsInt();
            int amp = e.get("amplifier").getAsInt();
            sb.append(shortName).append(" ").append(roman(amp + 1)).append(" (").append(dur / 20).append("s)");
            if (i < arr.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }
    
    private String roman(int n) {
        String[] r = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
        if (n <= 0) return "";
        if (n <= 10) return r[n - 1];
        return String.valueOf(n);
    }
    
    private String formatDouble(double d) {
        return String.format("%.3f", d);
    }
    
    private String mapSpeed(double s) {
        if (s < 0) return "&7未知";
        if (s < 0.15) return "&e缓慢";
        if (s < 0.22) return "&e普通";
        if (s < 0.30) return "&e迅捷";
        return "&e疾行";
    }
    
    private String mapJump(double j) {
        if (j < 0) return "&7未知";
        if (j < 0.50) return "&e低";
        if (j < 0.80) return "&e中";
        return "&e高";
    }
}
