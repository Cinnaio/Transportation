package com.github.cinnaio.transportation.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private final MiniMessage miniMessage;
    private final Map<Character, String> legacyMap;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacyMap = new HashMap<>();
        initLegacyMap();
        setup();
    }

    private void initLegacyMap() {
        legacyMap.put('0', "<black>");
        legacyMap.put('1', "<dark_blue>");
        legacyMap.put('2', "<dark_green>");
        legacyMap.put('3', "<dark_aqua>");
        legacyMap.put('4', "<dark_red>");
        legacyMap.put('5', "<dark_purple>");
        legacyMap.put('6', "<gold>");
        legacyMap.put('7', "<gray>");
        legacyMap.put('8', "<dark_gray>");
        legacyMap.put('9', "<blue>");
        legacyMap.put('a', "<green>");
        legacyMap.put('b', "<aqua>");
        legacyMap.put('c', "<red>");
        legacyMap.put('d', "<light_purple>");
        legacyMap.put('e', "<yellow>");
        legacyMap.put('f', "<white>");
        legacyMap.put('k', "<obfuscated>");
        legacyMap.put('l', "<bold>");
        legacyMap.put('m', "<strikethrough>");
        legacyMap.put('n', "<underlined>");
        legacyMap.put('o', "<italic>");
        legacyMap.put('r', "<reset>");
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.yml")) {
                if (in != null) {
                    Files.copy(in, messagesFile.toPath());
                } else {
                    messagesFile.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save messages.yml!");
                e.printStackTrace();
            }
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        addDefaults();
    }

    private void addDefaults() {
        messages.addDefault("prefix", "<color:#FFD479>[<color:#6EC6FF>Transportation<color:#FFD479>] <color:#A0A0A0>");
        messages.addDefault("no-permission", "<color:#FF6B6B>你没有权限执行此指令。");
        messages.addDefault("player-only", "<color:#FF6B6B>只有玩家可以使用此指令。");
        messages.addDefault("invalid-args", "<color:#FF6B6B>参数无效。请使用 /tra help 查看帮助。");
        messages.addDefault("vehicle-not-found", "<color:#FF6B6B>未找到指定的载具。");
        messages.addDefault("not-owner", "<color:#FF6B6B>你不是这辆车的主人。");
        messages.addDefault("garage-full", "<color:#FF6B6B>你的车库已满。");
        messages.addDefault("money-insufficient", "<color:#FF6B6B>余额不足。");
        messages.addDefault("buy-success", "<color:#6BFF95>购买成功！花费: <color:#FFD479>%price%");
        messages.addDefault("already-out", "<color:#FF6B6B>载具已经在外面了。");
        messages.addDefault("already-in", "<color:#FF6B6B>载具已经在车库里了。");
        messages.addDefault("summon-success", "<color:#6BFF95>载具已召唤。");
        messages.addDefault("recall-success", "<color:#6BFF95>载具已召回。");
        messages.addDefault("cooldown", "<color:#FF6B6B>请等待 %time% 秒后再召唤。");
        messages.addDefault("transfer-success", "<color:#6BFF95>载具已转让给 <color:#FFD479>%player%<color:#6BFF95>。");
        messages.addDefault("receive-vehicle", "<color:#6BFF95>你收到了 <color:#FFD479>%player% <color:#6BFF95>转让的载具。");
        messages.addDefault("bind-success", "<color:#6BFF95>载具绑定成功。");
        messages.addDefault("unbind-success", "<color:#6BFF95>载具解绑成功。");
        messages.addDefault("freeze-success", "<color:#6BFF95>载具状态已更新：冻结=<color:#FFD479>%status%<color:#6BFF95>。");
        messages.addDefault("key-bound", "<color:#6BFF95>钥匙绑定成功。");
        messages.addDefault("key-unbound", "<color:#6BFF95>钥匙解绑成功。");
        messages.addDefault("rekey-success", "<color:#6BFF95>身份码已重置。");
        messages.addDefault("destroyed", "<color:#FF6B6B>载具已损毁，无法召唤。");
        messages.addDefault("no-vehicles", "<color:#FF6B6B>你没有任何载具。");
        messages.addDefault("list-header", "<color:#FFD479>=== 你的载具列表 ===");
        messages.addDefault("reload-success", "<color:#6BFF95>插件重载成功！");

        messages.options().copyDefaults(true);
        save();
    }

    private void addHelpCommandDefault(String command, String usage, String description) {
        // Deprecated, keeping structure if needed but mostly unused now
        messages.addDefault("help.list." + command + ".usage", usage);
        messages.addDefault("help.list." + command + ".description", description);
    }

    public String get(String path) {
        // Return legacy string for compatibility
        Component component = getComponent(path);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public Component getComponent(String path) {
        String raw = messages.getString(path, path);
        return parse(raw);
    }

    public Component getComponent(String path, Map<String, String> replacements) {
        String raw = messages.getString(path, path);
        if (raw == null) return Component.empty();
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return parse(raw);
    }

    public java.util.List<Component> getComponentList(String path) {
        java.util.List<String> rawList = messages.getStringList(path);
        java.util.List<Component> components = new java.util.ArrayList<>();
        if (rawList == null || rawList.isEmpty()) return components;
        
        for (String raw : rawList) {
            components.add(parse(raw));
        }
        return components;
    }

    public Component parse(String text) {
        if (text == null) return Component.empty();

        // 1. Convert Hex: &#RRGGBB or {#RRGGBB} -> <color:#RRGGBB>
        // Regex for &#xxxxxx or {#xxxxxx}
        Pattern hexPattern = Pattern.compile("(&#|\\{#)([A-Fa-f0-9]{6})\\}?");
        Matcher matcher = hexPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<color:#" + matcher.group(2) + ">");
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 2. Convert Legacy: &c -> <red>, etc.
        // We iterate through the string and replace &x with <tag>
        // Note: This is a simple implementation. It doesn't handle escaped & properly (&&).
        StringBuilder finalBuilder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (legacyMap.containsKey(code)) {
                    finalBuilder.append(legacyMap.get(code));
                    i++; // Skip the code char
                } else {
                    finalBuilder.append(c);
                }
            } else {
                finalBuilder.append(c);
            }
        }
        text = finalBuilder.toString();

        // 3. Parse with MiniMessage
        return miniMessage.deserialize(text);
    }

    public String getRaw(String path) {
        return messages.getString(path, path);
    }

    public void save() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void reload() {
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }
}
