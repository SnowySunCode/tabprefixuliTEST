package me.snowsun.tabprefix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TabListener implements Listener {
    private final TabPrefix plugin;
    public TabListener(TabPrefix plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { plugin.updatePlayerDisplay(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getServer().getScoreboardManager() != null) {
            event.getPlayer().setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
        }
    }
}
