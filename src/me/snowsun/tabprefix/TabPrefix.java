package me.snowsun.tabprefix;

import org.bukkit.plugin.java.JavaPlugin;

public final class TabPrefix extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("TabPrefix " + getDescription().getVersion() + " starting...");
        getLogger().info("TabPrefix enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TabPrefix stopped.");
    }
}