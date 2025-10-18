package me.snowsun.tabprefix;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class TabCommand implements CommandExecutor {

    private final TabPrefix plugin;

    public TabCommand(TabPrefix plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("lptab")) return false;
        if (args.length == 0) {
            sender.sendMessage("§eИспользование: /lptab <reload|localadjust|approvechanges <code>>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("tabprefix.reload")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                plugin.updateAllPlayers();
                sender.sendMessage(ChatColor.GREEN + "Обновлено.");
                return true;

            case "localadjust":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игроков.");
                    return true;
                }
                if (!sender.hasPermission("tabprefix.adjust")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }

                Player p = (Player) sender;
                try {
                    String url = plugin.getSessionManager().startSessionFor(p);
                    if (url == null || url.isEmpty()) {
                        p.sendMessage(ChatColor.RED + "Не удалось запустить локальный редактор. Смотри логи сервера.");
                    } else {
                        p.sendMessage(ChatColor.GREEN + "Открой локальный редактор: " + ChatColor.AQUA + url);
                        p.sendMessage(ChatColor.GRAY + "Если браузер на другом устройстве, используй IP сервера:port.");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Не удалось запустить сессию для " + p.getName() + ": " + e.getMessage());
                    p.sendMessage(ChatColor.RED + "Ошибка при запуске локальной сессии. Проверь логи сервера.");
                }
                return true;

            case "approvechanges":
                if (!sender.hasPermission("tabprefix.approve")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Использование: /lptab approvechanges <code>");
                    return true;
                }
                String code = args[1];
                boolean ok = plugin.getPhotoManager().applyPendingCode(code, sender);
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "Применены изменения: " + code);
                    plugin.updateAllPlayers();
                } else {
                    sender.sendMessage(ChatColor.RED + "Код не найден/истёк.");
                }
                return true;

            default:
                sender.sendMessage("§eНеизвестная подкоманда.");
                return true;
        }
    }
}
