package me.snowsun.tabprefix;

import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Subscribe to LuckPerms EventBus for updates.
 */
public class LuckPermsListener {
    private final TabPrefix plugin;

    public LuckPermsListener(TabPrefix plugin) {
        this.plugin = plugin;
        try {
            EventBus bus = LuckPermsProvider.get().getEventBus();
            bus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
                if (event == null || event.getUser() == null) return;
                java.util.UUID uuid = event.getUser().getUniqueId();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) plugin.updatePlayerDisplay(p);
                });
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("LuckPermsListener subscribe failed: " + t.getMessage());
        }
    }
}
