package me.snowsun.tabprefix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds pack.zip mapping private chars to PNG files via font providers.
 * Returns SHA-1 bytes of generated pack or null on fail.
 */
public class ResourcePackGenerator {
    private final TabPrefix plugin;
    public ResourcePackGenerator(TabPrefix plugin) { this.plugin = plugin; }

    public byte[] build(Map<String, Path> charToImage) {
        try {
            Path tmp = plugin.getDataFolder().toPath().resolve("rp_tmp");
            if (Files.exists(tmp)) walkDelete(tmp);
            Files.createDirectories(tmp);

            // pack.mcmeta
            String mcmeta = "{\"pack\":{\"pack_format\":6,\"description\":\"TabPrefix pack\"}}";
            Files.write(tmp.resolve("pack.mcmeta"), mcmeta.getBytes("UTF-8"));

            Path assetsFont = tmp.resolve("assets/minecraft/font");
            Path texturesFont = tmp.resolve("assets/minecraft/textures/font");
            Files.createDirectories(assetsFont);
            Files.createDirectories(texturesFont);

            List<String> providers = new ArrayList<>();
            for (Map.Entry<String, Path> e : charToImage.entrySet()) {
                String ch = e.getKey();
                Path src = e.getValue();
                BufferedImage img = readFirstFrame(src);
                if (img == null) continue;
                String name = "tabimg_" + Math.abs(src.getFileName().toString().hashCode()) + ".png";
                Path dest = texturesFont.resolve(name);
                ImageIO.write(img, "PNG", dest.toFile());
                String provider = "{"
                        + "\"type\":\"bitmap\","
                        + "\"file\":\"textures/font/" + name + "\","
                        + "\"ascent\":8,"
                        + "\"chars\":[\"" + ch + "\"]"
                        + "}";
                providers.add(provider);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"providers\":[");
            for (int i=0;i<providers.size();i++) {
                if (i>0) sb.append(",");
                sb.append(providers.get(i));
            }
            sb.append("]}");
            Files.write(assetsFont.resolve("tabprefix.json"), sb.toString().getBytes("UTF-8"));

            // zip
            Path out = plugin.getDataFolder().toPath().resolve(TabPrefix.PACK_NAME);
            try (ZipOutputStream zs = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
                Files.walk(tmp).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    try {
                        String rel = tmp.relativize(p).toString().replace('\\','/');
                        zs.putNextEntry(new ZipEntry(rel));
                        Files.copy(p, zs);
                        zs.closeEntry();
                    } catch (IOException ex) { plugin.getLogger().warning("zip add fail: " + ex.getMessage()); }
                });
            }

            // cleanup
            walkDelete(tmp);

            // sha1
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] all = Files.readAllBytes(out);
            return md.digest(all);
        } catch (Exception e) {
            plugin.getLogger().warning("ResourcePackGenerator error: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage readFirstFrame(Path p) {
        try {
            return ImageIO.read(p.toFile()); // ImageIO auto handles gif first frame
        } catch (Exception ex) {
            plugin.getLogger().warning("read image fail: " + ex.getMessage());
            return null;
        }
    }

    private void walkDelete(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walk(p).sorted(Comparator.reverseOrder()).forEach(q -> {
            try { Files.delete(q); } catch (IOException ignored) {}
        });
    }
}
