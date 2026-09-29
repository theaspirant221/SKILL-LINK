package com.skilllink.api.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class JavaAstAnalyzer {
    public List<AnalysisModels.Signal> analyze(AnalysisModels.SourceFile file) {
        List<AnalysisModels.Signal> signals = new ArrayList<>();
        if (file.path().toLowerCase(Locale.ROOT).contains("/src/test/") || file.path().toLowerCase(Locale.ROOT).startsWith("src/test/")) signals.add(signal("unit-testing", "STATIC_ANALYSIS", "Test source file was parsed from the repository.", file, "MODERATE", "Test source path"));
        try {
            CompilationUnit unit = StaticJavaParser.parse(file.content());
            unit.accept(new VoidVisitorAdapter<List<AnalysisModels.Signal>>() {
                @Override public void visit(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration declaration, List<AnalysisModels.Signal> collector) {
                    super.visit(declaration, collector);
                    String name = declaration.getNameAsString();
                    if (hasAnnotation(declaration, "SpringBootApplication")) collector.add(signal("spring-boot", "STATIC_ANALYSIS", "Class is annotated with @SpringBootApplication.", file, "DIRECT", name));
                    if (hasAnnotation(declaration, "RestController") || hasAnnotation(declaration, "Controller")) collector.add(signal("rest-api-development", "STATIC_ANALYSIS", "Controller class was identified through a Spring MVC controller annotation.", file, "DIRECT", name));
                    if (hasAnnotation(declaration, "Service") || hasAnnotation(declaration, "Repository")) collector.add(signal("spring-boot", "STATIC_ANALYSIS", "Spring service/repository component was identified through an annotation.", file, "STRONG", name));
                    if (hasAnnotation(declaration, "Entity") || hasAnnotation(declaration, "Table")) collector.add(signal("jpa-hibernate", "STATIC_ANALYSIS", "JPA entity mapping was identified through an annotation.", file, "DIRECT", name));
                    if (name.toLowerCase(Locale.ROOT).contains("jwt") || name.toLowerCase(Locale.ROOT).contains("token")) collector.add(signal("jwt-authentication", "STATIC_ANALYSIS", "Security-related class name indicates token implementation context; source was parsed for supporting signals.", file, "MODERATE", name));
                }
                @Override public void visit(MethodDeclaration method, List<AnalysisModels.Signal> collector) {
                    super.visit(method, collector);
                    if (hasAnnotation(method, "GetMapping") || hasAnnotation(method, "PostMapping") || hasAnnotation(method, "PutMapping") || hasAnnotation(method, "PatchMapping") || hasAnnotation(method, "DeleteMapping") || hasAnnotation(method, "RequestMapping")) collector.add(signal("rest-api-development", "STATIC_ANALYSIS", "HTTP endpoint method was identified through a Spring mapping annotation.", file, "DIRECT", method.getNameAsString()));
                    if (hasAnnotation(method, "PreAuthorize") || hasAnnotation(method, "Secured") || hasAnnotation(method, "RolesAllowed")) collector.add(signal("spring-security", "STATIC_ANALYSIS", "Authorization annotation was identified on a method.", file, "DIRECT", method.getNameAsString()));
                }
                @Override public void visit(MethodCallExpr call, List<AnalysisModels.Signal> collector) {
                    super.visit(call, collector);
                    if (call.getNameAsString().equals("parser") || call.getNameAsString().equals("parseClaimsJws") || call.getNameAsString().equals("signWith") || call.getNameAsString().equals("compact")) collector.add(signal("jwt-authentication", "STATIC_ANALYSIS", "JWT library method call was identified in parsed Java source.", file, "DIRECT", call.getNameAsString()));
                }
            }, signals);
            String lower = file.content().toLowerCase(Locale.ROOT);
            if (lower.contains("org.springframework.security") || lower.contains("securityfilterchain")) signals.add(signal("spring-security", "STATIC_ANALYSIS", "Spring Security imports/configuration were identified by the parsed compilation unit.", file, "STRONG", "Spring Security import/configuration"));
            if (lower.contains("io.jsonwebtoken") || lower.contains("jwt") && lower.contains("authorization")) signals.add(signal("jwt-authentication", "STATIC_ANALYSIS", "Parsed source contains JWT/authentication implementation signals.", file, "STRONG", "JWT import or authorization flow"));
            if (lower.contains("org.springframework") && (lower.contains("@restcontroller") || lower.contains("@requestmapping"))) signals.add(signal("spring-mvc", "STATIC_ANALYSIS", "Spring MVC controller mapping was identified in parsed Java source.", file, "DIRECT", "Spring MVC annotation"));
            if (!signals.isEmpty()) signals.add(signal("java", "STATIC_ANALYSIS", "Java compilation unit parsed successfully with language-level declarations and methods.", file, "STRONG", "JavaParser AST"));
        } catch (Exception ignored) {
            // A malformed source file is not evidence. Manifest and other files may still yield evidence.
        }
        return signals;
    }
    private boolean hasAnnotation(NodeWithAnnotations<?> node, String name) { return node.getAnnotationByName(name).isPresent(); }
    private AnalysisModels.Signal signal(String skillKey, String sourceType, String observation, AnalysisModels.SourceFile file, String strength, String independentSignal) { return new AnalysisModels.Signal(skillKey, sourceType, file.path(), file.path(), observation, strength, independentSignal, file.blobSha()); }
}
