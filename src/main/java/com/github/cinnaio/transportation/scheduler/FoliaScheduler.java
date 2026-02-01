package com.github.cinnaio.transportation.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    @Override
    public void runTask(Location location, Runnable runnable) {
        Bukkit.getRegionScheduler().execute(plugin, location, runnable);
    }

    @Override
    public void runTask(Entity entity, Runnable runnable) {
        entity.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    @Override
    public void runTaskLater(Location location, Runnable runnable, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), delayTicks);
    }

    @Override
    public void runTaskLater(Entity entity, Runnable runnable, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks);
    }

    @Override
    public void runTaskTimer(Location location, Consumer<Object> task, long delayTicks, long periodTicks) {
        Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, (st) -> task.accept(st), delayTicks, periodTicks);
    }

    @Override
    public void runTaskTimer(Entity entity, Consumer<Object> task, long delayTicks, long periodTicks) {
        entity.getScheduler().runAtFixedRate(plugin, (st) -> task.accept(st), null, delayTicks, periodTicks);
    }

    @Override
    public void cancelTask(Object task) {
        if (task instanceof ScheduledTask) {
            ((ScheduledTask) task).cancel();
        }
    }
}
