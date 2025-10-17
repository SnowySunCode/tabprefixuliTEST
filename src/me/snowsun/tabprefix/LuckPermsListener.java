package me.snowsun.tabprefix;

import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LuckPermsListener {

    private final TabPrefix plugin;
    private final LuckPerms luckPerms;

    public LuckPermsListener(TabPrefix plugin) {
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        EventBus bus = luckPerms.getEventBus();
        bus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            if (event == null || event.getUser() == null) return;
            java.util.UUID uuid = event.getUser().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) plugin.updatePlayerDisplay(p);
            });
        });
    }
}
