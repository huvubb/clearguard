import com.youlong.hd.WebOriginPolicy;

public class WebOriginPolicyCheck {
    public static void main(String[] args) {
        assert WebOriginPolicy.isLocalDocument("https://app.local/index.html?page=PAGES1#settings");
        assert WebOriginPolicy.isLocalAsset("https://app.local/css/main.css");
        for (String url : new String[]{null, "https://app.local.evil/index.html", "https://app.local@evil/index.html",
                "https://evil@app.local/index.html", "https://app.local:443/index.html", "http://app.local/index.html",
                "file:///index.html", "content://app.local/index.html", "javascript:alert(1)",
                "https://app.local/../index.html", "https://app.local/%2e%2e/index.html",
                "https://app.local/dir\\index.html", "https://app.local//index.html"}) {
            assert !WebOriginPolicy.isLocalAsset(url) : url;
        }
        assert !WebOriginPolicy.isLocalDocument("https://app.local/other.html");
        System.out.println("PASS WebView origin and asset path boundaries");
    }
}
