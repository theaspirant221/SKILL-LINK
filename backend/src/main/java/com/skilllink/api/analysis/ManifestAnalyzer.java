package com.skilllink.api.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ManifestAnalyzer {
    private static final Pattern MAVEN_ARTIFACT = Pattern.compile("<artifactId>\\s*([^<]+)\\s*</artifactId>");
    private static final Pattern NPM_PACKAGE = Pattern.compile("[\\\"']([^\\\"']+)[\\\"']\\s*:");

    public List<AnalysisModels.Signal> analyze(AnalysisModels.SourceFile file) {
        String path = file.path().toLowerCase(Locale.ROOT);
        String content = file.content();
        List<AnalysisModels.Signal> signals = new ArrayList<>();
        if (path.endsWith("pom.xml") || path.endsWith("build.gradle") || path.endsWith("build.gradle.kts")) {
            addIf(content, "spring-boot-starter-web", "spring-boot", "Dependency manifest declares Spring Boot web starter.", file, signals);
            addIf(content, "spring-security", "spring-security", "Dependency manifest declares Spring Security.", file, signals);
            addIf(content, "spring-data-jpa", "jpa-hibernate", "Dependency manifest declares Spring Data JPA.", file, signals);
            addIf(content, "postgresql", "postgresql", "Dependency manifest declares a PostgreSQL driver.", file, signals);
            addIf(content, "mysql", "mysql", "Dependency manifest declares a MySQL driver.", file, signals);
            addIf(content, "mongodb", "mongodb", "Dependency manifest declares MongoDB support.", file, signals);
            addIf(content, "jjwt", "jwt-authentication", "Dependency manifest declares a JWT library.", file, signals);
            addIf(content, "junit", "unit-testing", "Dependency manifest declares JUnit test tooling.", file, signals);
            addIf(content, "spring-boot", "spring-boot", "Build manifest references Spring Boot.", file, signals);
        } else if (path.endsWith("package.json")) {
            addIf(content, "react", "react", "package.json declares React.", file, signals);
            addIf(content, "typescript", "typescript", "package.json declares TypeScript.", file, signals);
            addIf(content, "express", "rest-api-development", "package.json declares Express, a server/API framework.", file, signals);
            addIf(content, "jest", "unit-testing", "package.json declares Jest test tooling.", file, signals);
        } else if (path.endsWith("requirements.txt") || path.endsWith("pyproject.toml")) {
            addIf(content, "fastapi", "python", "Python manifest declares FastAPI.", file, signals);
            addIf(content, "flask", "python", "Python manifest declares Flask.", file, signals);
            addIf(content, "django", "python", "Python manifest declares Django.", file, signals);
            addIf(content, "pytest", "unit-testing", "Python manifest declares pytest.", file, signals);
        } else if (path.endsWith("dockerfile")) {
            signals.add(signal("docker", "Dockerfile is present in the repository snapshot.", file, "STRONG"));
        }
        return signals;
    }

    private void addIf(String content, String needle, String skillKey, String observation, AnalysisModels.SourceFile file, List<AnalysisModels.Signal> signals) {
        if (content.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) signals.add(signal(skillKey, observation, file, "MODERATE"));
    }
    private AnalysisModels.Signal signal(String skillKey, String observation, AnalysisModels.SourceFile file, String strength) { return new AnalysisModels.Signal(skillKey, "DEPENDENCY", file.path(), file.path(), observation, strength, "Manifest declaration", file.blobSha()); }
}
