package me.snowsun.tabprefix;

import org.bukkit.command.*;
import org.bukkit.ChatColor;

public class CertHelpCommand implements CommandExecutor {
    private final TabPrefix plugin;
    public CertHelpCommand(TabPrefix plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage(ChatColor.AQUA + "Инструкция доверия self-signed сертификата:");
        sender.sendMessage(ChatColor.YELLOW + "1) Скачай: http(s)://" + plugin.getPackServer().getHost() + ":" + plugin.getPackServer().getPort() + "/keystore.crt");
        sender.sendMessage(ChatColor.YELLOW + "2) macOS: sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain /path/to/keystore.crt");
        sender.sendMessage(ChatColor.YELLOW + "3) Windows: Импорт в Trusted Root Certification Authorities (certmgr.msc).");
        sender.sendMessage(ChatColor.GRAY + "После импорта перезапусти браузер.");
        return true;
    }
}
