package com.github.cinnaio.transportation;

import com.github.cinnaio.transportation.command.TransportationCommand;
import com.github.cinnaio.transportation.config.ConfigManager;
import com.github.cinnaio.transportation.config.LanguageManager;
import com.github.cinnaio.transportation.database.DatabaseManager;
import com.github.cinnaio.transportation.database.LogDAO;
import com.github.cinnaio.transportation.database.VehicleDAO;
import com.github.cinnaio.transportation.manager.EconomyManager;
import com.github.cinnaio.transportation.manager.VehicleManager;
import com.github.cinnaio.transportation.scheduler.FoliaScheduler;
import com.github.cinnaio.transportation.scheduler.PaperScheduler;
import com.github.cinnaio.transportation.scheduler.PlatformScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class Transportation extends JavaPlugin {

    private DatabaseManager databaseManager;
    private VehicleManager vehicleManager;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private EconomyManager economyManager;
    private PlatformScheduler scheduler;

    @Override
    public void onEnable() {
        // Initialize Scheduler
        setupScheduler();

        // Initialize Configs
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        
        // Initialize Economy
        this.economyManager = new EconomyManager(this);

        // Initialize Database
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // Initialize DAOs
        VehicleDAO vehicleDAO = new VehicleDAO(databaseManager);
        LogDAO logDAO = new LogDAO(databaseManager, scheduler);

        // Initialize Manager
        this.vehicleManager = new VehicleManager(this, vehicleDAO, logDAO, economyManager, configManager, languageManager);
        
        // Register Commands
        getCommand("transportation").setExecutor(new TransportationCommand(vehicleManager, languageManager, configManager));
        
        // Register Listeners
        getServer().getPluginManager().registerEvents(new com.github.cinnaio.transportation.listener.VehicleListener(this, vehicleManager, languageManager, logDAO), this);

        getLogger().info("Transportation plugin enabled successfully!");
    }

    private void setupScheduler() {
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        if (isFolia) {
            this.scheduler = new FoliaScheduler(this);
            getLogger().info("Detected Folia environment. Using FoliaScheduler.");
        } else {
            this.scheduler = new PaperScheduler(this);
            getLogger().info("Detected Standard Bukkit/Paper environment. Using PaperScheduler.");
        }
    }

    @Override
    public void onDisable() {
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        getLogger().info("Transportation plugin disabled.");
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }
    
    public PlatformScheduler getScheduler() {
        return scheduler;
    }
}
