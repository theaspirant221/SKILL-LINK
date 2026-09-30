package com.skilllink.api.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GithubWebhookSignaturesTest {
    private static final String SECRET = "webhook-test-secret";
    private static final byte[] BODY = "{\"action\":\"deleted\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void validSignatureIsAccepted() {
        String signature = GithubWebhookSignatures.sign(BODY, SECRET);
        assertTrue(GithubWebhookSignatures.isValid(BODY, signature, SECRET));
    }

    @Test
    void wrongSecretIsRejected() {
        String signature = GithubWebhookSignatures.sign(BODY, "a-different-secret");
        assertFalse(GithubWebhookSignatures.isValid(BODY, signature, SECRET));
    }

    @Test
    void tamperedBodyIsRejected() {
        String signature = GithubWebhookSignatures.sign(BODY, SECRET);
        byte[] tampered = "{\"action\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(GithubWebhookSignatures.isValid(tampered, signature, SECRET));
    }

    @Test
    void missingOrMalformedSignaturesAreRejected() {
        assertFalse(GithubWebhookSignatures.isValid(BODY, null, SECRET));
        assertFalse(GithubWebhookSignatures.isValid(BODY, "", SECRET));
        assertFalse(GithubWebhookSignatures.isValid(BODY, "sha256=not-hex!", SECRET));
        assertFalse(GithubWebhookSignatures.isValid(BODY, "sha256=abc", SECRET));
        assertFalse(GithubWebhookSignatures.isValid(BODY, "md5=abc", SECRET));
    }
}
