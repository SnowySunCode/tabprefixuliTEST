package me.snowsun.tabprefix;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages uploaded images, pending codes, group->char mapping, and assignments.
 */
public class PhotoPrefixManager {
    private final TabPrefix plugin;
    private final Path uploads;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, String> groupToChar = new ConcurrentHashMap<>();
    private final Map<UUID, PhotoAssignment> assignments = new ConcurrentHashMap<>();
    private static final int BASE = 0xE000;

    public PhotoPrefixManager(TabPrefix plugin) {
        this.plugin = plugin;
        this.uploads = plugin.getDataFolder().toPath().resolve("uploads");
        try { Files.createDirectories(uploads); } catch (Exception ignored) {}
    }

    public static class Pending {
        public final String group, filename;
        public final boolean animated;
        public final int frameDelay;
        public final double posX, posY, scale;
        public Pending(String group, String filename, boolean animated, int frameDelay, double posX, double posY, double scale) {
            this.group = group; this.filename = filename; this.animated = animated; this.frameDelay = frameDelay; this.posX = posX; this.posY = posY; this.scale = scale;
        }
    }

    public static class PhotoAssignment {
        public final String group;
        public final String filename;
        public final String ch;
        public final boolean animated;
        public PhotoAssignment(String group, String filename, String ch, boolean animated) { this.group=group; this.filename=filename; this.ch=ch; this.animated=animated; }
        public String getCurrentDisplayText() { return ch == null ? "" : ch; }
    }

    public String createPendingFromUpload(String group, String filename, byte[] bytes, boolean animated, int frameDelay, double posX, double posY, double scale) {
        try {
            Path out = uploads.resolve(filename);
            Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String code = Util.randomToken(8);
            pending.put(code, new Pending(group, filename, animated, frameDelay, posX, posY, scale));
            plugin.getLogger().info("Uploaded " + filename + " for group " + group + " as code " + code);
            return code;
        } catch (Exception e) {
            plugin.getLogger().warning("upload store fail: " + e.getMessage());
            return null;
        }
    }

    public boolean applyPendingCode(String code, CommandSender sender) {
        Pending p = pending.remove(code);
        if (p == null) return false;

        // assign char for group if missing
        String ch = groupToChar.get(p.group);
        if (ch == null) {
            int idx = groupToChar.size();
            if (idx >= 256) { sender.sendMessage("Too many images."); return false; }
            ch = new String(Character.toChars(BASE + idx));
            groupToChar.put(p.group, ch);
        }

        // assign to online players in group
        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            try {
                net.luckperms.api.model.user.User u = plugin.getLuckPerms().getUserManager().getUser(pl.getUniqueId());
                if (u != null) {
                    String primary = u.getPrimaryGroup();
                    if (primary != null && primary.equalsIgnoreCase(p.group)) {
                        assignments.put(pl.getUniqueId(), new PhotoAssignment(p.group, p.filename, ch, p.animated));
                    }
                }
            } catch (Exception ignored) {}
        }

        // gather map
        Map<String, Path> map = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : groupToChar.entrySet()) {
            String grp = entry.getKey();
            String charStr = entry.getValue();
            // find filename used for group: search assignments for that group
            String filename = null;
            for (PhotoAssignment pa : assignments.values()) {
                if (pa.group.equalsIgnoreCase(grp)) { filename = pa.filename; break; }
            }
            if (filename == null) continue;
            Path f = uploads.resolve(filename);
            if (Files.exists(f)) map.put(charStr, f);
        }

        ResourcePackGenerator gen = new ResourcePackGenerator(plugin);
        byte[] sha1 = gen.build(map);
        if (sha1 == null) {
            sender.sendMessage("Resource pack generation failed.");
            return true;
        }

        ResourcePackServer rps = plugin.getPackServer();
        String scheme = rps.isHttps() ? "https" : "http";
        String url = scheme + "://" + rps.getHost() + ":" + rps.getPort() + "/pack.zip";

        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            try { pl.setResourcePack(url, sha1); } catch (Exception ex) { plugin.getLogger().warning("setResourcePack failed: " + ex.getMessage()); }
        }

        sender.sendMessage("Applied photo for group " + p.group + " and distributed resource pack.");
        plugin.getLogger().info("Applied code " + code + " and distributed pack.");
        return true;
    }

    public PhotoAssignment getAssignmentForPlayer(Player p) { return assignments.get(p.getUniqueId()); }

    public void tickAnimations() {
        // placeholder for future GIF frame cycling
    }

    public Path getUploadPath() { return uploads; }
    public Path getPackPath() { return plugin.getDataFolder().toPath().resolve(TabPrefix.PACK_NAME); }
}
