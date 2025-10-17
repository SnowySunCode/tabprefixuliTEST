package me.snowsun.tabprefix;

import net.luckperms.api.model.user.User;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

public class ChatListener implements Listener {

    private final TabPrefix plugin;
    public ChatListener(TabPrefix plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());

        String prefix = "";
        String suffix = "";
        if (user != null) {
            if (user.getCachedData().getMetaData().getPrefix() != null) prefix = user.getCachedData().getMetaData().getPrefix();
            if (user.getCachedData().getMetaData().getSuffix() != null) suffix = user.getCachedData().getMetaData().getSuffix();
        }

        PhotoPrefixManager.PhotoAssignment ass = plugin.getPhotoManager().getAssignmentForPlayer(player);
        String photo = ass != null ? ass.getCurrentDisplayText() : "";

        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        String name = (photo != null ? photo : "") + prefix + ChatColor.RESET + player.getName() + suffix;
        event.setFormat(name + ChatColor.GRAY + ": " + ChatColor.WHITE + "%2$s");
    }
}
