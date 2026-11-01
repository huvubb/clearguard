import com.youlong.hd.ModelDownload;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
public class ModelDownloadCheck {
    static final Map<String, byte[]> bodies = new HashMap<>();
    static final Map<String, String> redirects = new HashMap<>();
    static final byte[] gguf = "GGUFtest-model".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static File directory;
    static class Fake extends HttpURLConnection {
        Fake(URL url) { super(url); }
        public void connect() { }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public int getResponseCode() { return redirects.containsKey(url.toString()) ? 302 : bodies.containsKey(url.toString()) ? 200 : 404; }
        public String getHeaderField(String key) { return key.equals("Location") ? redirects.get(url.toString()) : null; }
        public long getContentLengthLong() { return bodies.get(url.toString()).length; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bodies.get(url.toString())); }
    }
    static String hash(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x",b & 255));
        return value.toString();
    }
    static void rejects(String source) throws Exception {
        try { ModelDownload.download(source,directory,() -> false,ignored -> {}); throw new AssertionError(source); }
        catch (IOException expected) { }
        assert Arrays.stream(directory.list()).noneMatch(name -> name.endsWith(".part"));
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { return new Fake(url); }
        } : null);
        directory = Files.createTempDirectory("clearguard-model-check-").toFile();
        try {
            String hf = "https://huggingface.co/owner/model/resolve/main/a.gguf";
            bodies.put(hf,gguf);
            File model = ModelDownload.download(hf,directory,() -> false,ignored -> {});
            assert Arrays.equals(Files.readAllBytes(model.toPath()),gguf);
            Files.write(model.toPath(),new byte[]{1,2,3});
            model = ModelDownload.download(hf,directory,() -> false,ignored -> {});
            assert Arrays.equals(Files.readAllBytes(model.toPath()),gguf);
            String manifest = "https://registry.ollama.ai/v2/library/tiny/manifests/latest";
            String digest = "sha256:" + hash(gguf);
            String layer = "{\"mediaType\":\"application/vnd.ollama.image.model\",\"digest\":\""+digest+"\",\"size\":"+gguf.length+"}";
            bodies.put(manifest,("{\"layers\":["+layer+"]}").getBytes());
            String blob = "https://registry.ollama.ai/v2/library/tiny/blobs/"+digest;
            bodies.put(blob,gguf);
            assert ModelDownload.download("tiny",directory,() -> false,ignored -> {}).exists();
            bodies.put(blob,"GGUFwrong-data".getBytes()); rejects("tiny");
            bodies.put(hf,"<html>failure</html>".getBytes()); rejects(hf);
            redirects.put(hf,"https://127.0.0.1/private"); rejects(hf);
            redirects.put(hf,"http://huggingface.co/plaintext.gguf"); rejects(hf);
            redirects.put(hf,"https://huggingface.co@evil.invalid/a.gguf"); rejects(hf);
            redirects.clear(); bodies.put(hf,gguf);
            try { ModelDownload.download(hf,directory,() -> true,ignored -> {}); throw new AssertionError(); }
            catch (InterruptedIOException expected) { }
            bodies.put(manifest,("{\"layers\":["+layer+","+layer+"]}").getBytes()); rejects("tiny");
            bodies.put(manifest,"{\"layers\":[{\"mediaType\":\"application/vnd.ollama.image.projector\"}]}".getBytes()); rejects("tiny");
            bodies.put(manifest,("{\"layers\":["+layer.replace("\"size\":"+gguf.length,"\"size\":-1")+"]}").getBytes()); rejects("tiny");
            rejects("../model"); rejects("https://evil.invalid/model.gguf");
            System.out.println("PASS model download GGUF validation, checksum, redirects, cancellation and temporary-file cleanup");
        } finally { for (File file : directory.listFiles()) file.delete(); directory.delete(); }
    }
}
