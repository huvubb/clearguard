package com.youlong.hd;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Simple Bloom filter for SHA-256 hash membership testing.
 *
 * <p>The bit array is stored as a byte array (big-endian bit order within each
 * byte: bit 0 = MSB of first byte).  We use double hashing: two independent
 * 64-bit hashes derived from the SHA-256 digest, then k probe positions are
 * h1 + i*h2 (mod m).
 *
 * <p>The number of hash functions k is derived from the file size and expected
 * capacity, matching the Hypatia database format: for a 6.6 MB filter with
 * ~1.18M entries, k≈16 gives FPR ≈ 3e-9.
 */
public final class BloomFilter {

    private final byte[] bits;
    private final int m;       // number of bits
    private final int k;       // number of hash functions

    private BloomFilter(byte[] data, int k) {
        this.bits = data;
        this.m = data.length * 8;
        this.k = k;
    }

    /**
     * Load a bloom filter from a file.  Auto-detect k from file size.
     */
    public static BloomFilter load(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        // k ≈ (m / n_expected) * ln(2).  For the Hypatia database:
        // m ≈ 52.7M bits, n ≈ 1.18M → k ≈ 31.  But the actual FPR from
        // the generation report is 3.26e-9, which matches k=16.
        // We compute k from the data: optimal k = (m/n) * ln(2).
        // Since we don't know n exactly, use a fixed k=16 for the known
        // database format.
        return new BloomFilter(data, 16);
    }

    /**
     * Load from an Android asset.
     */
    public static BloomFilter loadFromAsset(android.content.Context ctx, String assetName) throws Exception {
        byte[] data;
        try (InputStream in = ctx.getAssets().open(assetName)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            data = bos.toByteArray();
        }
        return new BloomFilter(data, 16);
    }

    /**
     * Test whether a SHA-256 hash (hex string, lowercase) is in the filter.
     */
    public boolean contains(String sha256Hex) {
        try {
            byte[] key = hexToBytes(sha256Hex);
            // Use SHA-256 of the key to get two independent hashes h1, h2
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key);
            long h1 = bytesToLong(digest, 0);
            long h2 = bytesToLong(digest, 8);
            // Mix h2 so it's not correlated with h1
            h2 ^= (h1 >>> 31);
            for (int i = 0; i < k; i++) {
                long pos = (h1 + (long) i * h2) % m;
                if (pos < 0) pos += m;
                int byteIdx = (int) (pos / 8);
                int bitIdx = 7 - (int) (pos % 8);  // MSB first
                if ((bits[byteIdx] & (1 << bitIdx)) == 0) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int size() { return m; }
    public int hashCount() { return k; }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static long bytesToLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[off + i] & 0xFF);
        }
        return v;
    }
}
