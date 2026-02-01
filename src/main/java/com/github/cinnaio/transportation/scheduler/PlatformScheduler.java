package com.github.cinnaio.transportation.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface PlatformScheduler {
    void runAsync(Runnable runnable);
    void runTask(Location location, Runnable runnable);
    void runTask(Entity entity, Runnable runnable);
    void runTaskLater(Location location, Runnable runnable, long delayTicks);
    void runTaskLater(Entity entity, Runnable runnable, long delayTicks);
    void runTaskTimer(Location location, Consumer<Object> task, long delayTicks, long periodTicks);
    void runTaskTimer(Entity entity, Consumer<Object> task, long delayTicks, long periodTicks);
    void cancelTask(Object task);
}
