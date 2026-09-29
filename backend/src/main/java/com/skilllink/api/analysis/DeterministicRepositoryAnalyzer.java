package com.skilllink.api.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeterministicRepositoryAnalyzer {
    private final ManifestAnalyzer manifestAnalyzer;
    private final JavaAstAnalyzer javaAstAnalyzer;
    private final GenericSourceAnalyzer genericSourceAnalyzer;
    public DeterministicRepositoryAnalyzer(ManifestAnalyzer manifestAnalyzer, JavaAstAnalyzer javaAstAnalyzer, GenericSourceAnalyzer genericSourceAnalyzer) { this.manifestAnalyzer = manifestAnalyzer; this.javaAstAnalyzer = javaAstAnalyzer; this.genericSourceAnalyzer = genericSourceAnalyzer; }

    public AnalysisModels.AnalysisOutput analyze(AnalysisModels.FetchedSnapshot snapshot) {
        Map<String, AnalysisModels.Signal> deduped = new LinkedHashMap<>();
        java.util.Set<String> languages = new java.util.TreeSet<>();
        java.util.Set<String> frameworks = new java.util.TreeSet<>();
        int tests = 0;
        for (AnalysisModels.SourceFile file : snapshot.files()) {
            if (file.language() != null) languages.add(file.language());
            String lower = file.path().toLowerCase(Locale.ROOT);
            if (lower.contains("/test/") || lower.contains("/tests/") || lower.endsWith("test.java") || lower.endsWith("_test.py") || lower.contains(".test.") || lower.contains(".spec.")) tests++;
            List<AnalysisModels.Signal> signals = new ArrayList<>();
            signals.addAll(manifestAnalyzer.analyze(file));
            if (lower.endsWith(".java")) signals.addAll(javaAstAnalyzer.analyze(file)); else signals.addAll(genericSourceAnalyzer.analyze(file));
            for (AnalysisModels.Signal signal : signals) {
                String key = signal.skillKey() + "|" + signal.sourceLocation() + "|" + signal.observation();
                deduped.putIfAbsent(key, signal);
                if (signal.skillKey().startsWith("spring")) frameworks.add("Spring");
                if (signal.skillKey().equals("react")) frameworks.add("React");
                if (signal.skillKey().equals("python")) frameworks.add("Python web");
            }
        }
        String summary = String.format("Deterministic analysis inspected %d selected files from %s at commit %s and produced %d source-backed observations.", snapshot.files().size(), snapshot.fullName(), snapshot.commitSha().substring(0, Math.min(8, snapshot.commitSha().length())), deduped.size());
        return new AnalysisModels.AnalysisOutput(List.copyOf(languages), List.copyOf(frameworks), List.copyOf(deduped.values()), snapshot.files().size(), tests, snapshot.contentSizeBytes(), snapshot.redactedValues(), summary);
    }
}
