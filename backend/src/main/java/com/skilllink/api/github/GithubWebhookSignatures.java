package com.skilllink.api.github;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** GitHub webhook signature verification (X-Hub-Signature-256, HMAC-SHA256, constant-time). */
public final class GithubWebhookSignatures {
    private GithubWebhookSignatures() {}

    public static boolean isValid(byte[] rawBody, String signatureHeader, String secret) {
        if (rawBody == null || signatureHeader == null || secret == null) return false;
        String provided = signatureHeader.trim();
        if (!provided.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            return MessageDigest.isEqual(computed, decodeHex(provided.substring("sha256=".length()).trim()));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String sign(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + encodeHex(mac.doFinal(rawBody));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) return new byte[0];
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) return new byte[0];
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static String encodeHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}
