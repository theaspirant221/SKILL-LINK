package com.skilllink.api.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GenericSourceAnalyzer {
    public List<AnalysisModels.Signal> analyze(AnalysisModels.SourceFile file) {
        String path = file.path().toLowerCase(Locale.ROOT), content = file.content(), lower = content.toLowerCase(Locale.ROOT);
        List<AnalysisModels.Signal> result = new ArrayList<>();
        if (path.endsWith(".js") || path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")) {
            if (lower.contains("from 'react'") || lower.contains("from \"react\"") || lower.contains("react.") || lower.contains("<react")) result.add(signal("react", "React import/component signals were identified in source.", file, "STRONG", "React import or JSX"));
            if (lower.contains("express") && (lower.contains(".get(") || lower.contains(".post(") || lower.contains("router."))) result.add(signal("rest-api-development", "Express route or router call was identified in source.", file, "DIRECT", "Express route declaration"));
            if (lower.contains("fetch(") || lower.contains("axios")) result.add(signal("javascript", "Browser/API client calls were identified in source.", file, "MODERATE", "Fetch or Axios usage"));
            if (path.endsWith(".ts") || path.endsWith(".tsx")) result.add(signal("typescript", "TypeScript source file was inspected.", file, "STRONG", "TypeScript extension"));
            else result.add(signal("javascript", "JavaScript source file was inspected.", file, "STRONG", "JavaScript extension"));
        } else if (path.endsWith(".py")) {
            result.add(signal("python", "Python source file was inspected.", file, "STRONG", "Python extension"));
            if (lower.contains("fastapi") || lower.contains("fastapi(")) result.add(signal("python", "FastAPI application signals were identified in Python source.", file, "DIRECT", "FastAPI import/application"));
            if (lower.contains("flask(") || lower.contains("from flask")) result.add(signal("python", "Flask application signals were identified in Python source.", file, "DIRECT", "Flask import/application"));
            if (lower.matches("(?s).*@(app|router)\\.(get|post|put|patch|delete).*")) result.add(signal("rest-api-development", "Python route decorator was identified in source.", file, "DIRECT", "Route decorator"));
        }
        if (path.contains("/test/") || path.contains("/tests/") || path.endsWith("_test.py") || path.endsWith(".test.js") || path.endsWith(".spec.ts") || path.endsWith("test.tsx")) result.add(signal("unit-testing", "Test source file was identified by path and inspected.", file, "MODERATE", "Test path or suffix"));
        return result;
    }
    private AnalysisModels.Signal signal(String skillKey, String observation, AnalysisModels.SourceFile file, String strength, String independentSignal) { return new AnalysisModels.Signal(skillKey, "STATIC_ANALYSIS", file.path(), file.path(), observation, strength, independentSignal, file.blobSha()); }
}
