package com.skilllink.api.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotFilePolicyTest {
    private final SnapshotFilePolicy policy = new SnapshotFilePolicy(500_000, 20_000_000, 1000, 10000, 20, "v1");

    @Test
    void includesRelevantSourceFiles() {
        assertTrue(policy.decide("src/main/java/App.java", 1000).included());
        assertTrue(policy.decide("src/index.ts", 1000).included());
        assertTrue(policy.decide("src/components/Button.tsx", 1000).included());
        assertTrue(policy.decide("app.py", 1000).included());
        assertTrue(policy.decide("main.go", 1000).included());
        assertTrue(policy.decide("lib.rs", 1000).included());
        assertTrue(policy.decide("pom.xml", 1000).included());
        assertTrue(policy.decide("package.json", 1000).included());
        assertTrue(policy.decide("Dockerfile", 1000).included());
        assertTrue(policy.decide("README.md", 1000).included());
    }

    @Test
    void excludesIgnoredDirectories() {
        assertFalse(policy.decide(".git/config", 100).included());
        assertFalse(policy.decide("node_modules/react/index.js", 100).included());
        assertFalse(policy.decide("target/classes/App.class", 100).included());
        assertFalse(policy.decide("dist/bundle.js", 100).included());
        assertFalse(policy.decide("build/output.js", 100).included());
        assertFalse(policy.decide("vendor/lib.php", 100).included());
        assertFalse(policy.decide(".venv/bin/python", 100).included());
    }

    @Test
    void excludesBinaryExtensions() {
        assertFalse(policy.decide("image.png", 100).included());
        assertFalse(policy.decide("photo.jpg", 100).included());
        assertFalse(policy.decide("archive.zip", 100).included());
        assertFalse(policy.decide("lib.jar", 100).included());
        assertFalse(policy.decide("video.mp4", 100).included());
    }

    @Test
    void excludesEnvAndSecretFiles() {
        assertFalse(policy.decide(".env", 100).included());
        assertFalse(policy.decide(".env.local", 100).included());
        assertFalse(policy.decide("config/.env", 100).included());
    }

    @Test
    void enforcesSizeLimits() {
        assertFalse(policy.decide("src/App.java", 600_000).included());
        assertEquals("FILE_TOO_LARGE", policy.decide("src/App.java", 600_000).reason());
    }

    @Test
    void enforcesDepthLimit() {
        String deepPath = "a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/file.java";
        assertFalse(policy.decide(deepPath, 100).included());
        assertEquals("MAX_DEPTH_EXCEEDED", policy.decide(deepPath, 100).reason());
    }

    @Test
    void detectsLanguage() {
        assertEquals("Java", policy.detectLanguage("src/App.java"));
        assertEquals("TypeScript", policy.detectLanguage("src/index.ts"));
        assertEquals("JavaScript", policy.detectLanguage("src/app.js"));
        assertEquals("Python", policy.detectLanguage("main.py"));
        assertEquals("Go", policy.detectLanguage("main.go"));
        assertEquals("SQL", policy.detectLanguage("query.sql"));
    }

    @Test
    void versionIsV1() {
        assertEquals("v1", policy.version());
    }
}
