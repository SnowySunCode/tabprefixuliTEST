package me.snowsun.tabprefix;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PhotoPrefixManager {

    public static class PhotoAssignment {
        public final String group;
        public final String filename; // processed small image filename
        public final boolean animated;
        public final int frameDelay;
        public final double posX, posY, scale;

        public PhotoAssignment(String group, String filename, boolean animated, int frameDelay, double posX, double posY, double scale) {
            this.group = group; this.filename = filename; this.animated = animated; this.frameDelay = frameDelay;
            this.posX = posX; this.posY = posY; this.scale = scale;
        }

        // returns single-character private-use placeholder used in font mapping
        public String getDisplayChar() {
            int cp = 0xE000 + Math.abs(filename.hashCode()) % 4096;
            return new String(Character.toChars(cp));
        }
    }

    public static class PendingImage {
        public final String filename;
        public final boolean animated;
        public final int frameDelay;
        public final double posX, posY, scale;
        public PendingImage(String filename, boolean animated, int frameDelay, double posX, double posY, double scale) {
            this.filename = filename; this.animated = animated; this.frameDelay = frameDelay;
            this.posX = posX; this.posY = posY; this.scale = scale;
        }
    }

    private final Path imagesDir;
    private final Path dataDir;
    private final Path assignmentsFile;
    private final Map<String, PhotoAssignment> assignments = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PendingImage>> pending = new ConcurrentHashMap<>();
    private final TabPrefix plugin;

    // preview canvas internal reference size used by frontend
    private static final double PREVIEW_W = 480.0;
    private static final double PREVIEW_H = 240.0;

    public PhotoPrefixManager(TabPrefix plugin) {
        this.plugin = plugin;
        this.imagesDir = plugin.getDataFolder().toPath().resolve("images");
        this.dataDir = plugin.getDataFolder().toPath().resolve("data");
        this.assignmentsFile = dataDir.resolve("assignments.yml");
        try { Files.createDirectories(imagesDir); Files.createDirectories(dataDir); } catch (IOException e) { plugin.getLogger().warning("mkdir failed: "+e.getMessage()); }
        loadAssignments();
    }

    /**
     * Store original image bytes and process into small texture (32x32) according to posX,posY,scale.
     * Returns stored processed filename (e.g. "163483_avt.png") or null on error.
     */
    public String storeAndProcessImage(String originalFilename, byte[] bytes, double posX, double posY, double scale) {
        try {
            // save original (for backups)
            String safe = sanitize(originalFilename);
            String ts = "" + System.currentTimeMillis();
            Path orig = imagesDir.resolve(ts + "_orig_" + safe);
            Files.write(orig, bytes);

            // read
            InputStream in = new ByteArrayInputStream(bytes);
            BufferedImage src = ImageIO.read(in);
            if (src == null) return null;

            // target size (font bitmap) — choose 32x32 (works for modern clients)
            int target = 32;
            BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0,0,0,0));
            g.fillRect(0,0,target,target);

            // compute normalized center from preview coords (480x240)
            double normX = clamp(posX / PREVIEW_W, 0.0, 1.0);
            double normY = clamp(posY / PREVIEW_H, 0.0, 1.0);

            // compute scale mapping: frontend scale roughly relative to preview; map to target
            double normScale = Math.max(0.05, scale) * (Math.min(target / PREVIEW_W, target / PREVIEW_H) * 4.0);
            // above heuristic: make scale noticeable on small texture; may need tuning per image.

            int drawW = Math.max(1, (int)Math.round(src.getWidth() * normScale));
            int drawH = Math.max(1, (int)Math.round(src.getHeight() * normScale));

            int centerX = (int)Math.round(normX * target);
            int centerY = (int)Math.round(normY * target);

            int drawX = centerX - drawW/2;
            int drawY = centerY - drawH/2;

            // Draw with clipping so image doesn't overflow
            g.drawImage(src, drawX, drawY, drawW, drawH, null);
            g.dispose();

            String outname = ts + "_proc_" + safe;
            if (!outname.toLowerCase().endsWith(".png")) outname += ".png";
            Path outpath = imagesDir.resolve(outname);
            ImageIO.write(out, "PNG", outpath.toFile());

            plugin.getLogger().info("Processed image saved: " + outpath.toString());
            return outname;
        } catch (Exception ex) {
            plugin.getLogger().warning("process image failed: " + ex.getMessage());
            return null;
        }
    }

    private double clamp(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    private String sanitize(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    /**
     * Create a pending change directly (used by PlayerSessionServer).
     * Accepts original bytes + meta (pos/scale) and returns a pending code.
     */
    public String createPendingFromUpload(String group, String filename, byte[] bytes, boolean animated, int frameDelay, double posX, double posY, double scale) {
        String processed = storeAndProcessImage(filename, bytes, posX, posY, scale);
        if (processed == null) return null;
        Map<String, PendingImage> map = new HashMap<>();
        map.put(group, new PendingImage(processed, animated, frameDelay, posX, posY, scale));
        String code = randomCode(8);
        pending.put(code, map);
        savePendingToDisk(code, map);
        plugin.getLogger().info("Created pending change " + code + " for group " + group + " -> " + processed);
        return code;
    }

    private void savePendingToDisk(String code, Map<String, PendingImage> map) {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("code", code);
            for (Map.Entry<String, PendingImage> e : map.entrySet()) {
                String g = e.getKey();
                PendingImage pi = e.getValue();
                y.set("images." + g + ".filename", pi.filename);
                y.set("images." + g + ".animated", pi.animated);
                y.set("images." + g + ".frameDelay", pi.frameDelay);
                y.set("images." + g + ".posX", pi.posX);
                y.set("images." + g + ".posY", pi.posY);
                y.set("images." + g + ".scale", pi.scale);
            }
            y.save(dataDir.resolve("pending_" + code + ".yml").toFile());
        } catch (Exception ex) { plugin.getLogger().warning("save pending failed: "+ex.getMessage()); }
    }

    public boolean applyPendingCode(String code, org.bukkit.command.CommandSender appliedBy) {
        Map<String, PendingImage> map = pending.get(code);
        if (map == null) {
            Path p = dataDir.resolve("pending_" + code + ".yml");
            if (!Files.exists(p)) return false;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(p.toFile());
            map = new HashMap<>();
            if (y.contains("images")) {
                for (String g : y.getConfigurationSection("images").getKeys(false)) {
                    String fname = y.getString("images." + g + ".filename");
                    boolean anim = y.getBoolean("images." + g + ".animated", false);
                    int fd = y.getInt("images." + g + ".frameDelay", 200);
                    double px = y.getDouble("images." + g + ".posX", PREVIEW_W/2.0);
                    double py = y.getDouble("images." + g + ".posY", PREVIEW_H/2.0);
                    double sc = y.getDouble("images." + g + ".scale", 1.0);
                    map.put(g, new PendingImage(fname, anim, fd, px, py, sc));
                }
            }
        }

        // apply assignments
        for (Map.Entry<String, PendingImage> e : map.entrySet()) {
            String group = e.getKey();
            PendingImage pi = e.getValue();
            PhotoAssignment pa = new PhotoAssignment(group, pi.filename, pi.animated, pi.frameDelay, pi.posX, pi.posY, pi.scale);
            assignments.put(group, pa);
            plugin.getLogger().info("Assigned " + pi.filename + " -> group " + group);
        }

        persistAssignments();
        try { generateResourcePack(); } catch (Exception ex) { plugin.getLogger().warning("gen pack failed: "+ex.getMessage()); }

        pending.remove(code);
        try { Files.deleteIfExists(dataDir.resolve("pending_" + code + ".yml")); } catch (IOException ignored) {}

        plugin.getServer().broadcastMessage(ChatColor.GREEN + "[TabPrefix] " + ChatColor.WHITE + "Applied pending " + code + " by " + appliedBy.getName());
        return true;
    }

    private void persistAssignments() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<String, PhotoAssignment> e : assignments.entrySet()) {
                String g = e.getKey();
                PhotoAssignment p = e.getValue();
                y.set("assignments." + g + ".filename", p.filename);
                y.set("assignments." + g + ".animated", p.animated);
                y.set("assignments." + g + ".frameDelay", p.frameDelay);
                y.set("assignments." + g + ".posX", p.posX);
                y.set("assignments." + g + ".posY", p.posY);
                y.set("assignments." + g + ".scale", p.scale);
            }
            y.save(assignmentsFile.toFile());
        } catch (Exception ex) { plugin.getLogger().warning("persist assignments failed: "+ex.getMessage()); }
    }

    private void loadAssignments() {
        if (!Files.exists(assignmentsFile)) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(assignmentsFile.toFile());
        if (!y.contains("assignments")) return;
        for (String g : y.getConfigurationSection("assignments").getKeys(false)) {
            String fname = y.getString("assignments." + g + ".filename");
            boolean anim = y.getBoolean("assignments." + g + ".animated", false);
            int fd = y.getInt("assignments." + g + ".frameDelay", 200);
            double px = y.getDouble("assignments." + g + ".posX", PREVIEW_W/2.0);
            double py = y.getDouble("assignments." + g + ".posY", PREVIEW_H/2.0);
            double sc = y.getDouble("assignments." + g + ".scale", 1.0);
            assignments.put(g, new PhotoAssignment(g, fname, anim, fd, px, py, sc));
        }
    }

    public PhotoAssignment getAssignmentForPlayer(Player p) {
        try {
            net.luckperms.api.model.user.User u = plugin.getLuckPerms().getUserManager().getUser(p.getUniqueId());
            if (u == null) return null;
            String primary = u.getPrimaryGroup();
            return assignments.get(primary);
        } catch (Exception ex) { return null; }
    }

    public void tickAnimations() {
        boolean any = false;
        for (PhotoAssignment pa : assignments.values()) {
            if (pa.animated) { any = true; /* simple tick handled elsewhere or not implemented */ }
        }
        if (any) plugin.updateAllPlayers();
    }

    private String randomCode(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private String randomCode(int len, Random r) { StringBuilder sb=new StringBuilder(len); String chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; for(int i=0;i<len;i++) sb.append(chars.charAt(r.nextInt(chars.length()))); return sb.toString(); }

    /**
     * Generate resource pack (zip) with processed images and font mapping to private-use chars.
     * This uses assignments and processed image files (32x32 pngs).
     */
    private void generateResourcePack() throws Exception {
        Path pack = plugin.getDataFolder().toPath().resolve("pack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(pack, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            String mcmeta = "{ \"pack\": { \"pack_format\": 6, \"description\": \"TabPrefix dynamic pack\" } }";
            addZip(zos, "pack.mcmeta", mcmeta.getBytes());

            List<Map<String,Object>> providers = new ArrayList<>();
            for (PhotoAssignment pa : assignments.values()) {
                Path src = imagesDir.resolve(pa.filename);
                if (!Files.exists(src)) continue;
                byte[] b = Files.readAllBytes(src);
                String entryName = "assets/minecraft/textures/font/" + pa.filename;
                addZip(zos, entryName, b);

                Map<String,Object> prov = new LinkedHashMap<>();
                prov.put("type","bitmap");
                String nameNoExt = pa.filename;
                if (nameNoExt.endsWith(".png")) nameNoExt = nameNoExt.substring(0, nameNoExt.length()-4);
                prov.put("file", "minecraft:font/" + nameNoExt);
                prov.put("ascent", 8);
                String ch = pa.getDisplayChar();
                prov.put("chars", Collections.singletonList(ch));
                providers.add(prov);
            }
            Map<String,Object> root = new LinkedHashMap<>();
            root.put("providers", providers);
            String fontJson = Util.toJson(root);
            addZip(zos, "assets/minecraft/font/tabprefix.json", fontJson.getBytes());
        }
        plugin.getLogger().info("Resource pack generated.");
    }

    private void addZip(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    public Path getPackPath() { return plugin.getDataFolder().toPath().resolve("pack.zip"); }

}
