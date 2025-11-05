package me.snowsun.tabprefix;

import java.security.SecureRandom;

public class Util {
    private static final String ALPH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom R = new SecureRandom();

    public static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(ALPH.charAt(R.nextInt(ALPH.length())));
        return sb.toString();
    }

    public static String makeSafeTeamName(String playerName) {
        String base = "tp_" + playerName;
        if (base.length() <= 16) return base;
        return base.substring(0, 16);
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n","\\n").replace("\r","\\r");
    }

    // very simple json string extractor (for our controlled payloads)
    public static String extractJsonString(String json, String key) {
        String q = "\"" + key + "\":";
        int i = json.indexOf(q);
        if (i == -1) return null;
        int s = json.indexOf('"', i+q.length());
        if (s == -1) return null;
        int e = json.indexOf('"', s+1);
        if (e == -1) return null;
        return json.substring(s+1, e);
    }
    public static boolean extractJsonBoolean(String json, String key, boolean def) {
        String q = "\"" + key + "\":";
        int i = json.indexOf(q);
        if (i == -1) return def;
        String tail = json.substring(i+q.length()).trim();
        if (tail.startsWith("true")) return true;
        if (tail.startsWith("false")) return false;
        return def;
    }
    public static int extractJsonInt(String json, String key, int def) {
        String v = extractJsonString(json, key);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }
    public static double extractJsonDouble(String json, String key, double def) {
        String v = extractJsonString(json, key);
        if (v == null) return def;
        try { return Double.parseDouble(v); } catch (Exception e) { return def; }
    }
}
