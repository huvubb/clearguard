package com.youlong.hd;

import java.net.URI;

/** Only packaged assets may share a WebView with native bridges. */
public final class WebOriginPolicy {
    private WebOriginPolicy() {}

    public static boolean isLocalAsset(String url) {
        if (url == null || url.indexOf('\\') >= 0) return false;
        try {
            URI uri = new URI(url);
            String path = uri.getRawPath();
            return "https".equals(uri.getScheme()) && "app.local".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getRawUserInfo() == null
                    && path != null && path.matches("/[A-Za-z0-9_./-]*")
                    && !path.contains("//") && !path.contains("/../") && !path.endsWith("/..")
                    && !path.contains("/./") && !path.endsWith("/.");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isLocalDocument(String url) {
        if (!isLocalAsset(url)) return false;
        try {
            return "/index.html".equals(new URI(url).getRawPath());
        } catch (Exception ignored) {
            return false;
        }
    }
}
