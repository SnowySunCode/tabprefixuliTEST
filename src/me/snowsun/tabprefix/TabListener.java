package me.snowsun.tabprefix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.command.*;

public class TabListener implements Listener, CommandExecutor {

    private final TabPrefix plugin;

    public TabListener(TabPrefix plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("lptab") != null) plugin.getCommand("lptab").setExecutor(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.updatePlayerDisplay(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.getServer().getScoreboardManager() != null) {
            e.getPlayer().setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
        }
        plugin.getSessionManager().stopSessionFor(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("lptab")) return false;
        sender.sendMessage("§eИспользование: /lptab <reload|localadjust|approvechanges>");
        return true;
    }
}
