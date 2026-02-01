package com.github.cinnaio.transportation.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public class PaperScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @Override
    public void runTask(Location location, Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void runTask(Entity entity, Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void runTaskLater(Location location, Runnable runnable, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    @Override
    public void runTaskLater(Entity entity, Runnable runnable, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    @Override
    public void runTaskTimer(Location location, Consumer<Object> task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> task.accept(null), delayTicks, periodTicks);
    }

    @Override
    public void runTaskTimer(Entity entity, Consumer<Object> task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> task.accept(null), delayTicks, periodTicks);
    }
    
    @Override
    public void cancelTask(Object task) {
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        }
    }
}
