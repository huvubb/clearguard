package com.youlong.hd;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.*;

/** Public model downloads only. No scripts, repositories, or remote model code are executed. */
public final class ModelDownload {
    static final long MAX_BYTES = 16L * 1024 * 1024 * 1024;
    private ModelDownload() { }
    public static File download(String source, File directory, BooleanSupplier cancelled, Consumer<String> progress) throws Exception {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("已取消下载");
        String digest = null;
        long expected = -1;
        URL url;
        if (source.startsWith("https://huggingface.co/")) {
            url = new URL(source);
            if (!url.getPath().matches("/[^/]+/[^/]+/resolve/.+\\.gguf"))
                throw new IOException("请填写 Hugging Face 的 /resolve/...gguf 文件下载链接");
        } else {
            if (!source.matches("[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)?(:[A-Za-z0-9._-]+)?"))
                throw new IOException("请输入公开 Ollama 模型名，例如 qwen2.5:0.5b，或 Hugging Face GGUF 下载链接");
            int colon = source.lastIndexOf(':');
            String name = colon < 0 ? source : source.substring(0, colon);
            String tag = colon < 0 ? "latest" : source.substring(colon + 1);
            if (!name.contains("/")) name = "library/" + name;
            HttpURLConnection manifest = connect(new URL("https://registry.ollama.ai/v2/" + name + "/manifests/" + tag));
            JSONObject json;
            try (InputStream input = manifest.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (bytes.size() + size > 1024 * 1024) throw new IOException("模型清单过大");
                    bytes.write(buffer, 0, size);
                }
                json = new JSONObject(bytes.toString("UTF-8"));
            } finally { manifest.disconnect(); }
            JSONArray layers = json.getJSONArray("layers");
            for (int i = 0; i < layers.length(); i++) {
                JSONObject layer = layers.getJSONObject(i);
                String media = layer.getString("mediaType");
                if (media.contains("projector") || media.contains("adapter"))
                    throw new IOException("此模型需要额外投影或适配层，请选择单文件文本 GGUF 模型");
                if (media.equals("application/vnd.ollama.image.model")) {
                    if (digest != null) throw new IOException("暂不支持多模型层清单");
                    digest = layer.getString("digest"); expected = layer.getLong("size");
                    if (expected < 4 || expected > MAX_BYTES) throw new IOException("模型清单大小无效或超过 16 GiB");
                }
            }
            if (digest == null || !digest.matches("sha256:[0-9a-f]{64}")) throw new IOException("没有可校验的 GGUF 模型层");
            url = new URL("https://registry.ollama.ai/v2/" + name + "/blobs/" + digest);
        }
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建模型目录");
        HttpURLConnection connection = connect(url);
        File partial = null;
        try {
            long length = connection.getContentLengthLong();
            if (expected > 0 && length > 0 && expected != length) throw new IOException("模型大小与清单不符");
            long wanted = expected > 0 ? expected : length;
            if (wanted > MAX_BYTES || (wanted > 0 && wanted + 64 * 1024 * 1024 > directory.getUsableSpace()))
                throw new IOException("模型超过 16 GiB 或存储空间不足，请选择更小的量化模型");
            partial = File.createTempFile("download-", ".part", directory);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0, lastUpdate = 0;
            try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(partial)) {
                byte[] header = new byte[4]; new DataInputStream(input).readFully(header);
                if (!java.util.Arrays.equals(header, new byte[]{'G','G','U','F'})) throw new IOException("下载内容不是 GGUF 模型");
                output.write(header); sha.update(header); total = 4;
                byte[] buffer = new byte[65536]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("已取消下载");
                    total += size;
                    if (total > MAX_BYTES || directory.getUsableSpace() < size + 16 * 1024 * 1024) throw new IOException("模型过大或存储空间不足");
                    output.write(buffer, 0, size); sha.update(buffer, 0, size);
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 500) { progress.accept("已下载 " + total / 1048576 + " MiB" + (wanted > 0 ? " / " + wanted / 1048576 + " MiB" : "")); lastUpdate = now; }
                }
            }
            if (wanted > 0 && total != wanted) throw new IOException("下载不完整");
            StringBuilder hash = new StringBuilder(); for (byte value : sha.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (digest != null && !digest.equals("sha256:" + hash)) throw new IOException("模型 SHA-256 校验失败");
            File target = new File(directory, hash + ".gguf");
            if (target.exists() && !target.delete()) throw new IOException("无法替换已有模型");
            if (!partial.renameTo(target)) throw new IOException("无法保存模型");
            progress.accept("下载完成，SHA-256：" + hash);
            return target;
        } finally { connection.disconnect(); if (partial != null && partial.exists()) partial.delete(); }
    }
    private static HttpURLConnection connect(URL url) throws IOException {
        for (int redirects = 0; redirects < 8; redirects++) {
            String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
            if (!url.getProtocol().equals("https") || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)
                    || !(host.equals("huggingface.co") || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")
                    || host.equals("registry.ollama.ai") || host.endsWith(".cloudflarestorage.com")))
                throw new IOException("模型下载跳转到了未支持的域名：" + host);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.docker.distribution.manifest.v2+json, application/octet-stream");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location"); connection.disconnect();
                if (location == null) throw new IOException("下载重定向缺少地址");
                url = new URL(url, location); continue;
            }
            if (status != 200) { connection.disconnect(); throw new IOException("模型服务返回 HTTP " + status + "，请检查文件地址、公开访问权限或模型名称"); }
            return connection;
        }
        throw new IOException("下载重定向次数过多");
    }
}
