package me.snowsun.tabprefix;

import java.util.Random;

public class Util {
    public static String randomToken(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    public static String makeSafeTeamName(String playerName) {
        String base = "tp_" + playerName;
        if (base.length() <= 16) return base;
        return base.substring(0, 16);
    }

    public static String extractJsonString(String json, String key) {
        String q = "\"" + key + "\"";
        int i = json.indexOf(q);
        if (i == -1) return null;
        int c = json.indexOf(':', i);
        if (c == -1) return null;
        int firstQuote = json.indexOf('"', c);
        if (firstQuote == -1) return null;
        int secondQuote = json.indexOf('"', firstQuote+1);
        if (secondQuote == -1) return null;
        return json.substring(firstQuote+1, secondQuote);
    }

    public static boolean extractJsonBoolean(String json, String key, boolean def) {
        String q = "\"" + key + "\"";
        int i = json.indexOf(q);
        if (i == -1) return def;
        int c = json.indexOf(':', i);
        if (c == -1) return def;
        String sub = json.substring(c+1).trim();
        if (sub.startsWith("true")) return true;
        if (sub.startsWith("false")) return false;
        return def;
    }

    public static int extractJsonInt(String json, String key, int def) {
        String q = "\"" + key + "\"";
        int i = json.indexOf(q);
        if (i == -1) return def;
        int c = json.indexOf(':', i);
        if (c == -1) return def;
        String sub = json.substring(c+1).trim();
        StringBuilder num = new StringBuilder();
        for (char ch : sub.toCharArray()) {
            if ((ch >= '0' && ch <= '9') || ch=='-') num.append(ch); else break;
        }
        if (num.length()==0) return def;
        try { return Integer.parseInt(num.toString()); } catch (Exception e) { return def; }
    }

    public static double extractJsonDouble(String json, String key, double def) {
        String q = "\"" + key + "\"";
        int i = json.indexOf(q);
        if (i == -1) return def;
        int c = json.indexOf(':', i);
        if (c == -1) return def;
        String sub = json.substring(c+1).trim();
        StringBuilder num = new StringBuilder();
        for (char ch : sub.toCharArray()) {
            if ((ch >= '0' && ch <= '9') || ch=='-' || ch=='.') num.append(ch); else break;
        }
        if (num.length()==0) return def;
        try { return Double.parseDouble(num.toString()); } catch (Exception e) { return def; }
    }
}
