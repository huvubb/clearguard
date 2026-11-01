package com.youlong.hd;
import java.net.IDN;
import java.util.*;

public final class SiteDomainPolicy {
    private final Map<String,String> categories;
    private final Set<String> allow, deny;
    public SiteDomainPolicy(Map<String,String> categories, String allowed, String denied) {
        this.categories = categories; allow = parse(allowed); deny = parse(denied);
    }
    public static String normalize(String domain) {
        String value = domain.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("*.")) value = value.substring(2);
        if (value.endsWith(".")) value = value.substring(0,value.length()-1);
        value = IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (value.length()>253 || !value.contains(".") || value.matches("[0-9.]+")) throw new IllegalArgumentException("无效域名："+domain);
        for (String label : value.split("\\.",-1))
            if (label.isEmpty() || label.length()>63) throw new IllegalArgumentException("无效域名："+domain);
        return value;
    }
    public static Set<String> parse(String text) {
        if (text.length()>32768) throw new IllegalArgumentException("自定义名单最多 32 KiB");
        Set<String> domains = new HashSet<>();
        for (String line : text.split("\\r?\\n")) if (!line.trim().isEmpty()) domains.add(normalize(line));
        return domains;
    }
    public String blockedCategory(String domain) {
        String normalized;
        try { normalized = normalize(domain); } catch (IllegalArgumentException invalid) { return null; }
        for (String part=normalized;;) {
            if (allow.contains(part)) return null;
            int dot=part.indexOf('.'); if (dot<0) break; part=part.substring(dot+1);
        }
        for (String part=normalized;;) {
            if (deny.contains(part)) return "自定义";
            String category=categories.get(part); if (category!=null) return category;
            int dot=part.indexOf('.'); if (dot<0) break; part=part.substring(dot+1);
        }
        return null;
    }
}
