package me.snowsun.tabprefix;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.ScoreboardManager;

public class TabPrefix extends JavaPlugin {

    private LuckPerms luckPerms;
    private ScoreboardManager scoreboardManager;
    private PhotoPrefixManager photoManager;
    private SessionWebServerManager sessionManager;
    private DBHelper db;

    @Override
    public void onEnable() {
        // DB
        try {
            this.db = new DBHelper(getDataFolder().toPath().resolve("tabprefix.db").toFile());
        } catch (Exception ex) {
            getLogger().severe("Не удалось инициализировать БД: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Try LuckPerms
        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (Exception ex) {
            getLogger().severe("LuckPerms API не найден. Установите LuckPerms и перезапустите сервер.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.photoManager = new PhotoPrefixManager(this);
        this.sessionManager = new SessionWebServerManager(this, db);

        // listeners & commands
        getServer().getPluginManager().registerEvents(new TabListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        new LuckPermsListener(this);

        if (getCommand("lptab") != null) getCommand("lptab").setExecutor(new TabCommand(this));

        // schedule animation tick (lightweight)
        Bukkit.getScheduler().runTaskTimer(this, () -> photoManager.tickAnimations(), 1L, 4L);

        // initial update
        Bukkit.getScheduler().runTask(this, this::updateAllPlayers);

        getLogger().info("TabPrefix включён (web sessions + HTTPS).");
    }

    @Override
    public void onDisable() {
        try { sessionManager.shutdownAll(); } catch (Exception ignored) {}
        try { db.close(); } catch (Exception ignored) {}
        getLogger().info("TabPrefix выключен.");
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public PhotoPrefixManager getPhotoManager() { return photoManager; }
    public SessionWebServerManager getSessionManager() { return sessionManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public DBHelper getDB() { return db; }

    public void updatePlayerDisplay(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;

        String prefix = "";
        String suffix = "";
        try {
            net.luckperms.api.model.user.User u = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (u != null) {
                if (u.getCachedData().getMetaData().getPrefix() != null) prefix = u.getCachedData().getMetaData().getPrefix();
                if (u.getCachedData().getMetaData().getSuffix() != null) suffix = u.getCachedData().getMetaData().getSuffix();
            }
        } catch (Exception ignore) {}

        PhotoPrefixManager.PhotoAssignment ass = photoManager.getAssignmentForPlayer(player);
        String photoText = (ass != null) ? ass.getCurrentDisplayText() : "";

        prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = org.bukkit.ChatColor.translateAlternateColorCodes('&', suffix);

        String fullPrefix = (photoText == null ? "" : photoText) + (prefix == null ? "" : prefix);
        String fullForTab = fullPrefix + org.bukkit.ChatColor.RESET + player.getName() + (suffix == null ? "" : suffix);

        String playerListName = fullForTab.length() <= 16 ? fullForTab : fullForTab.substring(0, 16);
        try { player.setPlayerListName(playerListName); } catch (Throwable t) { player.setPlayerListName(player.getName()); }

        org.bukkit.scoreboard.Scoreboard board = scoreboardManager.getNewScoreboard();
        String teamName = Util.makeSafeTeamName(player.getName());
        org.bukkit.scoreboard.Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.setPrefix(fullPrefix != null ? fullPrefix : "");
        team.setSuffix(suffix != null ? suffix : "");
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        player.setScoreboard(board);

        String display = fullPrefix + org.bukkit.ChatColor.RESET + player.getName() + (suffix == null ? "" : suffix);
        player.setDisplayName(display);
        player.setCustomName(display);
        player.setCustomNameVisible(true);
    }

    public void updateAllPlayers() {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) updatePlayerDisplay(p);
    }
}
