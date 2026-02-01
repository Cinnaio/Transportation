package com.github.cinnaio.transportation.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyManager {

    private Economy economy = null;
    private final JavaPlugin plugin;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasMoney(org.bukkit.OfflinePlayer player, double amount) {
        if (economy == null) return true; // Bypass if no economy
        return economy.has(player, amount);
    }

    public boolean withdraw(org.bukkit.OfflinePlayer player, double amount) {
        if (economy == null) return true; // Bypass if no economy
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
