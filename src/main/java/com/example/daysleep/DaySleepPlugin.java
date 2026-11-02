package com.example.daysleep;

import org.bukkit.plugin.java.JavaPlugin;

public final class DaySleepPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new BedListener(this), this);
        getLogger().info("DaySleepPlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DaySleepPlugin disabled.");
    }
}
