package com.skilllink.api.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${skilllink.security.encryption-key}") String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) throw new IllegalArgumentException("TOKEN_ENCRYPTION_KEY must decode to 16, 24, or 32 bytes");
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt integration secret", ex); }
    }

    public String decrypt(String value) {
        try {
            String[] parts = value.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("Unsupported encrypted value");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Base64.getUrlDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt integration secret", ex); }
    }
}
