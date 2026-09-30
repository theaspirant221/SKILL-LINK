package com.skilllink.api.analysis.detector;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.skilllink.api.analysis.AnalysisObservationRepository;
import com.skilllink.api.analysis.AnalysisFileErrorRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JavaAstDetector {
    public static final String DETECTOR = "JAVA_AST";
    public static final String VERSION = "deterministic-java-v1";

    private static final Set<String> SPRING_ANNOTATIONS = Set.of(
        "SpringBootApplication", "RestController", "Controller", "Service", "Repository", "Component",
        "Configuration", "Bean", "Entity", "Table", "GetMapping", "PostMapping", "PutMapping",
        "PatchMapping", "DeleteMapping", "RequestMapping", "PreAuthorize", "Secured", "Transactional",
        "Id", "OneToMany", "ManyToOne", "JoinColumn", "OneToOne", "ManyToMany", "MappedSuperclass",
        "Embeddable", "SpringBootTest", "WebMvcTest", "DataJpaTest"
    );

    public static class DetectionResult {
        public final List<AnalysisObservationRepository.ObservationRow> observations;
        public final List<AnalysisFileErrorRepository.FileErrorRow> errors;
        public DetectionResult(List<AnalysisObservationRepository.ObservationRow> observations, List<AnalysisFileErrorRepository.FileErrorRow> errors) {
            this.observations = observations;
            this.errors = errors;
        }
    }

    public DetectionResult detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        List<AnalysisFileErrorRepository.FileErrorRow> errors = new ArrayList<>();

        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            if (!path.toLowerCase().endsWith(".java")) continue;
            String content = file.content();
            if (content == null || content.isBlank()) continue;

            try {
                CompilationUnit cu = StaticJavaParser.parse(content);
                String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

                // Package declaration
                if (!packageName.isBlank()) {
                    obs.add(observation(runId, snapshotId, "PACKAGE_DECLARATION", "LANGUAGE", packageName, packageName,
                        "Java", null, path, null, null, packageName, file.contentHash(), "PACKAGE"));
                }

                // Imports
                cu.getImports().forEach(imp -> {
                    String importName = imp.getNameAsString();
                    obs.add(observation(runId, snapshotId, "IMPORT", "LANGUAGE", importName, importName,
                        "Java", null, path, imp.getRange().map(r -> r.begin.line).orElse(null),
                        imp.getRange().map(r -> r.end.line).orElse(null), importName, file.contentHash(), "IMPORT"));
                });

                // Classes, interfaces, enums, records
                for (TypeDeclaration<?> type : cu.getTypes()) {
                    String typeName = type.getNameAsString();
                    String symbol = packageName.isEmpty() ? typeName : packageName + "." + typeName;
                    int startLine = type.getRange().map(r -> r.begin.line).orElse(null);
                    int endLine = type.getRange().map(r -> r.end.line).orElse(null);

                    if (type instanceof ClassOrInterfaceDeclaration cid) {
                        if (cid.isInterface()) {
                            obs.add(observation(runId, snapshotId, "INTERFACE", "LANGUAGE", typeName, "interface",
                                "Java", null, path, startLine, endLine, symbol, file.contentHash(), "INTERFACE"));
                        } else {
                            obs.add(observation(runId, snapshotId, "CLASS", "LANGUAGE", typeName, "class",
                                "Java", null, path, startLine, endLine, symbol, file.contentHash(), "CLASS"));
                        }

                        // Inheritance
                        cid.getExtendedTypes().forEach(ext -> {
                            obs.add(observation(runId, snapshotId, "INHERITANCE", "LANGUAGE", typeName, ext.getNameAsString(),
                                "Java", null, path, startLine, endLine, symbol, file.contentHash(), "EXTENDS"));
                        });
                        cid.getImplementedTypes().forEach(impl -> {
                            obs.add(observation(runId, snapshotId, "IMPLEMENTED_INTERFACE", "LANGUAGE", typeName, impl.getNameAsString(),
                                "Java", null, path, startLine, endLine, symbol, file.contentHash(), "IMPLEMENTS"));
                        });

                        // Annotations
                        for (AnnotationExpr ann : cid.getAnnotations()) {
                            String annName = ann.getNameAsString();
                            obs.add(observation(runId, snapshotId, "ANNOTATION", "FRAMEWORK", annName, typeName,
                                "Java", frameworkFromAnnotation(annName), path, startLine, endLine, symbol, file.contentHash(), annName));

                            // Spring-specific
                            if (SPRING_ANNOTATIONS.contains(annName)) {
                                String category = categoryFromAnnotation(annName);
                                String obsType = observationTypeFromAnnotation(annName);
                                obs.add(observation(runId, snapshotId, obsType, category, annName, typeName,
                                    "Java", frameworkFromAnnotation(annName), path, startLine, endLine, symbol, file.contentHash(), annName));
                            }

                            // Extract HTTP mapping path
                            if (isMappingAnnotation(annName)) {
                                String httpPath = extractPathFromAnnotation(ann);
                                String httpMethod = httpMethodFromAnnotation(annName);
                                if (httpPath != null || httpMethod != null) {
                                    // Will be handled at method level too, but class-level RequestMapping is important
                                    obs.add(new AnalysisObservationRepository.ObservationRow(
                                        UUID.randomUUID(), runId, snapshotId,
                                        "HTTP_ENDPOINT", "API",
                                        httpMethod != null ? httpMethod : "REQUEST",
                                        httpPath != null ? httpPath : "/",
                                        "Java", "Spring MVC", path,
                                        startLine, endLine, symbol, file.contentHash(),
                                        DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                                    ));
                                }
                            }
                        }

                        // Fields
                        for (FieldDeclaration field : cid.getFields()) {
                            for (VariableDeclarator var : field.getVariables()) {
                                String fieldName = var.getNameAsString();
                                String fieldType = var.getTypeAsString();
                                int fStart = field.getRange().map(r -> r.begin.line).orElse(startLine);
                                int fEnd = field.getRange().map(r -> r.end.line).orElse(endLine);
                                String fieldSymbol = symbol + "." + fieldName;

                                obs.add(observation(runId, snapshotId, "FIELD", "LANGUAGE", fieldName, fieldType,
                                    "Java", null, path, fStart, fEnd, fieldSymbol, file.contentHash(), "FIELD"));

                                // Check field annotations for JPA
                                for (AnnotationExpr ann : field.getAnnotations()) {
                                    String annName = ann.getNameAsString();
                                    if (Set.of("Id", "OneToMany", "ManyToOne", "OneToOne", "ManyToMany", "JoinColumn", "Column", "JoinTable").contains(annName)) {
                                        obs.add(observation(runId, snapshotId, "JPA_RELATIONSHIP", "DATABASE", annName, fieldName,
                                            "Java", "JPA", path, fStart, fEnd, fieldSymbol, file.contentHash(), annName));
                                    }
                                }
                            }
                        }

                        // Methods, constructors
                        for (BodyDeclaration<?> member : cid.getMembers()) {
                            if (member instanceof MethodDeclaration method) {
                                String methodName = method.getNameAsString();
                                String signature = method.getDeclarationAsString(false, false, false);
                                int mStart = method.getRange().map(r -> r.begin.line).orElse(startLine);
                                int mEnd = method.getRange().map(r -> r.end.line).orElse(endLine);
                                String methodSymbol = symbol + "." + methodName;

                                obs.add(observation(runId, snapshotId, "METHOD", "LANGUAGE", methodName, signature,
                                    "Java", null, path, mStart, mEnd, methodSymbol, file.contentHash(), "METHOD"));

                                // Visibility
                                String visibility = "package-private";
                                if (method.isPublic()) visibility = "public";
                                else if (method.isPrivate()) visibility = "private";
                                else if (method.isProtected()) visibility = "protected";
                                obs.add(observation(runId, snapshotId, "METHOD_VISIBILITY", "LANGUAGE", methodName, visibility,
                                    "Java", null, path, mStart, mEnd, methodSymbol, file.contentHash(), visibility));

                                // Annotations on method
                                for (AnnotationExpr ann : method.getAnnotations()) {
                                    String annName = ann.getNameAsString();
                                    obs.add(observation(runId, snapshotId, "METHOD_ANNOTATION", "FRAMEWORK", annName, methodName,
                                        "Java", frameworkFromAnnotation(annName), path, mStart, mEnd, methodSymbol, file.contentHash(), annName));

                                    if (isMappingAnnotation(annName)) {
                                        String httpPath = extractPathFromAnnotation(ann);
                                        String httpMethod = httpMethodFromAnnotation(annName);
                                        // Try to resolve class-level base path
                                        String basePath = extractClassBasePath(cid);
                                        String fullPath = combinePaths(basePath, httpPath);

                                        obs.add(new AnalysisObservationRepository.ObservationRow(
                                            UUID.randomUUID(), runId, snapshotId,
                                            "HTTP_ENDPOINT", "API",
                                            httpMethod != null ? httpMethod : "REQUEST",
                                            fullPath,
                                            "Java", "Spring MVC", path,
                                            mStart, mEnd, methodSymbol, file.contentHash(),
                                            DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                                        ));

                                        // Also add detailed endpoint observation with controller and method
                                        String controllerName = typeName;
                                        obs.add(new AnalysisObservationRepository.ObservationRow(
                                            UUID.randomUUID(), runId, snapshotId,
                                            "API_SIGNAL", "API",
                                            controllerName + "." + methodName,
                                            (httpMethod != null ? httpMethod : "UNKNOWN") + " " + fullPath,
                                            "Java", "Spring MVC", path,
                                            mStart, mEnd, methodSymbol, file.contentHash(),
                                            DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                                        ));
                                    }

                                    if (Set.of("PreAuthorize", "Secured", "RolesAllowed").contains(annName)) {
                                        obs.add(observation(runId, snapshotId, "SECURITY_ANNOTATION", "SECURITY", annName, methodName,
                                            "Java", "Spring Security", path, mStart, mEnd, methodSymbol, file.contentHash(), annName));
                                    }

                                    if ("Transactional".equals(annName)) {
                                        obs.add(observation(runId, snapshotId, "TRANSACTIONAL", "DATABASE", methodName, "transactional",
                                            "Java", "Spring", path, mStart, mEnd, methodSymbol, file.contentHash(), annName));
                                    }
                                }

                                // Exception handling
                                if (!method.getThrownExceptions().isEmpty()) {
                                    for (var thrown : method.getThrownExceptions()) {
                                        obs.add(observation(runId, snapshotId, "THROWS", "LANGUAGE", methodName, thrown.asString(),
                                            "Java", null, path, mStart, mEnd, methodSymbol, file.contentHash(), "THROWS"));
                                    }
                                }

                                // Generic usage
                                if (method.getType().asString().contains("<") || method.toString().contains("<")) {
                                    obs.add(observation(runId, snapshotId, "GENERIC_USAGE", "LANGUAGE", methodName, method.getTypeAsString(),
                                        "Java", null, path, mStart, mEnd, methodSymbol, file.contentHash(), "GENERIC"));
                                }
                            } else if (member instanceof ConstructorDeclaration ctor) {
                                String ctorName = ctor.getNameAsString();
                                int cStart = ctor.getRange().map(r -> r.begin.line).orElse(startLine);
                                int cEnd = ctor.getRange().map(r -> r.end.line).orElse(endLine);
                                String ctorSymbol = symbol + ".<init>";
                                obs.add(observation(runId, snapshotId, "CONSTRUCTOR", "LANGUAGE", ctorName, ctor.getDeclarationAsString(false, false, false),
                                    "Java", null, path, cStart, cEnd, ctorSymbol, file.contentHash(), "CONSTRUCTOR"));
                            }
                        }
                    } else if (type instanceof EnumDeclaration enumDecl) {
                        obs.add(observation(runId, snapshotId, "ENUM", "LANGUAGE", typeName, "enum",
                            "Java", null, path, startLine, endLine, symbol, file.contentHash(), "ENUM"));
                    } else if (type instanceof RecordDeclaration recordDecl) {
                        obs.add(observation(runId, snapshotId, "RECORD", "LANGUAGE", typeName, "record",
                            "Java", null, path, startLine, endLine, symbol, file.contentHash(), "RECORD"));
                    } else if (type instanceof AnnotationDeclaration annDecl) {
                        obs.add(observation(runId, snapshotId, "ANNOTATION_DECLARATION", "LANGUAGE", typeName, "annotation",
                            "Java", null, path, startLine, endLine, symbol, file.contentHash(), "ANNOTATION"));
                    }
                }

                // Security signals - look for specific classes
                String lowerContent = content.toLowerCase();
                if (lowerContent.contains("securityfilterchain")) {
                    obs.add(observation(runId, snapshotId, "SECURITY_CONFIGURATION", "SECURITY", "SecurityFilterChain", "found",
                        "Java", "Spring Security", path, null, null, "SecurityFilterChain", file.contentHash(), "SecurityFilterChain"));
                }
                if (lowerContent.contains("passwordencoder")) {
                    obs.add(observation(runId, snapshotId, "SECURITY_CONFIGURATION", "SECURITY", "PasswordEncoder", "found",
                        "Java", "Spring Security", path, null, null, "PasswordEncoder", file.contentHash(), "PasswordEncoder"));
                }
                if (lowerContent.contains("jwt") && (lowerContent.contains("token") || lowerContent.contains("filter"))) {
                    // Check if it's a JWT service/filter
                    if (path.toLowerCase().contains("jwt") || lowerContent.contains("class") && lowerContent.contains("jwt")) {
                        obs.add(observation(runId, snapshotId, "SECURITY_CONFIGURATION", "SECURITY", "JWT", "found",
                            "Java", "JWT", path, null, null, "JWT", file.contentHash(), "JWT"));
                    }
                }
                if (lowerContent.contains("cors") && lowerContent.contains("configuration")) {
                    obs.add(observation(runId, snapshotId, "SECURITY_CONFIGURATION", "SECURITY", "CORS", "found",
                        "Java", "Spring Security", path, null, null, "CORS", file.contentHash(), "CORS"));
                }
                if (lowerContent.contains("csrf")) {
                    obs.add(observation(runId, snapshotId, "SECURITY_CONFIGURATION", "SECURITY", "CSRF", "found",
                        "Java", "Spring Security", path, null, null, "CSRF", file.contentHash(), "CSRF"));
                }

                // Test signals
                if (path.toLowerCase().contains("test") || lowerContent.contains("@test") || lowerContent.contains("junit") || lowerContent.contains("testcontainers")) {
                    if (lowerContent.contains("junit") || lowerContent.contains("@test")) {
                        obs.add(observation(runId, snapshotId, "TEST_FRAMEWORK", "TESTING", "JUnit", "present",
                            "Java", "JUnit", path, null, null, "JUnit", file.contentHash(), "JUnit"));
                    }
                    if (lowerContent.contains("springboottest")) {
                        obs.add(observation(runId, snapshotId, "TEST_FRAMEWORK", "TESTING", "SpringBootTest", "present",
                            "Java", "Spring Boot Test", path, null, null, "SpringBootTest", file.contentHash(), "SpringBootTest"));
                    }
                    if (lowerContent.contains("webmvctest")) {
                        obs.add(observation(runId, snapshotId, "TEST_FRAMEWORK", "TESTING", "WebMvcTest", "present",
                            "Java", "Spring Test", path, null, null, "WebMvcTest", file.contentHash(), "WebMvcTest"));
                    }
                    if (lowerContent.contains("testcontainers")) {
                        obs.add(observation(runId, snapshotId, "TEST_FRAMEWORK", "TESTING", "Testcontainers", "present",
                            "Java", "Testcontainers", path, null, null, "Testcontainers", file.contentHash(), "Testcontainers"));
                    }
                    if (lowerContent.contains("mockito") || lowerContent.contains("@mock") || lowerContent.contains("mockbean")) {
                        obs.add(observation(runId, snapshotId, "TEST_FRAMEWORK", "TESTING", "Mockito", "present",
                            "Java", "Mockito", path, null, null, "Mockito", file.contentHash(), "Mockito"));
                    }
                }

                // Count test methods
                long testMethodCount = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getAnnotationByName("Test").isPresent() || m.getAnnotationByName("ParameterizedTest").isPresent())
                    .count();
                if (testMethodCount > 0) {
                    obs.add(observation(runId, snapshotId, "TEST_METHOD_COUNT", "TESTING", "test_methods", String.valueOf(testMethodCount),
                        "Java", "JUnit", path, null, null, "test_methods", file.contentHash(), "TEST_COUNT"));
                }

            } catch (Exception ex) {
                // Failure isolation: record file-level parse error, continue
                errors.add(new AnalysisFileErrorRepository.FileErrorRow(
                    UUID.randomUUID(), runId, snapshotId, path,
                    "PARSE_ERROR", ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 500)) : "Parse failed",
                    DETECTOR, null
                ));
            }
        }

        return new DetectionResult(obs, errors);
    }

    private AnalysisObservationRepository.ObservationRow observation(
        UUID runId, UUID snapshotId, String type, String category, String factKey, String factValue,
        String language, String framework, String path, Integer startLine, Integer endLine, String symbol, String sourceHash, String detectorDetail
    ) {
        return new AnalysisObservationRepository.ObservationRow(
            UUID.randomUUID(), runId, snapshotId,
            type, category, factKey, factValue,
            language, framework, path,
            startLine, endLine, symbol, sourceHash,
            DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
        );
    }

    private boolean isMappingAnnotation(String name) {
        return Set.of("GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping").contains(name);
    }

    private String httpMethodFromAnnotation(String name) {
        return switch (name) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "PatchMapping" -> "PATCH";
            case "DeleteMapping" -> "DELETE";
            case "RequestMapping" -> "REQUEST";
            default -> null;
        };
    }

    private String extractPathFromAnnotation(AnnotationExpr ann) {
        try {
            if (ann instanceof SingleMemberAnnotationExpr single) {
                String value = single.getMemberValue().toString().replaceAll("[\"']", "");
                return value;
            } else if (ann instanceof com.github.javaparser.ast.expr.NormalAnnotationExpr normal) {
                for (var pair : normal.getPairs()) {
                    String key = pair.getNameAsString();
                    if (key.equals("value") || key.equals("path")) {
                        return pair.getValue().toString().replaceAll("[\"']", "").replaceAll("[{}]", "").trim();
                    }
                }
                // If no value/path pair, try first pair
                if (!normal.getPairs().isEmpty()) {
                    return normal.getPairs().get(0).getValue().toString().replaceAll("[\"']", "").replaceAll("[{}]", "").trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractClassBasePath(ClassOrInterfaceDeclaration cid) {
        for (AnnotationExpr ann : cid.getAnnotations()) {
            if (isMappingAnnotation(ann.getNameAsString())) {
                String path = extractPathFromAnnotation(ann);
                if (path != null) return path;
            }
        }
        return "";
    }

    private String combinePaths(String base, String method) {
        if (base == null) base = "";
        if (method == null) method = "";
        base = base.trim();
        method = method.trim();
        if (base.isEmpty()) return method.isEmpty() ? "/" : method;
        if (method.isEmpty()) return base;
        // Ensure single slash
        if (base.endsWith("/") && method.startsWith("/")) return base + method.substring(1);
        if (!base.endsWith("/") && !method.startsWith("/")) return base + "/" + method;
        return base + method;
    }

    private String frameworkFromAnnotation(String ann) {
        return switch (ann) {
            case "SpringBootApplication", "Service", "Repository", "Component", "Configuration", "Bean" -> "Spring Boot";
            case "RestController", "Controller", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping" -> "Spring MVC";
            case "Entity", "Table", "Id", "OneToMany", "ManyToOne", "JoinColumn", "OneToOne", "ManyToMany" -> "JPA";
            case "PreAuthorize", "Secured" -> "Spring Security";
            case "Transactional" -> "Spring";
            case "SpringBootTest", "WebMvcTest", "DataJpaTest" -> "Spring Test";
            default -> null;
        };
    }

    private String categoryFromAnnotation(String ann) {
        return switch (ann) {
            case "SpringBootApplication" -> "FRAMEWORK";
            case "RestController", "Controller", "Service", "Repository", "Component", "Configuration", "Bean" -> "FRAMEWORK";
            case "Entity", "Table", "Id", "OneToMany", "ManyToOne", "JoinColumn", "OneToOne", "ManyToMany" -> "DATABASE";
            case "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping" -> "API";
            case "PreAuthorize", "Secured" -> "SECURITY";
            case "Transactional" -> "DATABASE";
            case "SpringBootTest", "WebMvcTest", "DataJpaTest" -> "TESTING";
            default -> "FRAMEWORK";
        };
    }

    private String observationTypeFromAnnotation(String ann) {
        return switch (ann) {
            case "SpringBootApplication" -> "SPRING_BOOT_APPLICATION";
            case "RestController" -> "SPRING_CONTROLLER";
            case "Controller" -> "SPRING_CONTROLLER";
            case "Service" -> "SPRING_SERVICE";
            case "Repository" -> "SPRING_REPOSITORY";
            case "Component" -> "SPRING_COMPONENT";
            case "Configuration" -> "SPRING_CONFIGURATION";
            case "Bean" -> "SPRING_BEAN";
            case "Entity" -> "JPA_ENTITY";
            case "Table" -> "JPA_TABLE";
            case "Id" -> "JPA_ID";
            case "OneToMany", "ManyToOne", "OneToOne", "ManyToMany", "JoinColumn" -> "JPA_RELATIONSHIP";
            case "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping" -> "HTTP_ENDPOINT";
            case "PreAuthorize", "Secured" -> "SECURITY_ANNOTATION";
            case "Transactional" -> "TRANSACTIONAL";
            case "SpringBootTest", "WebMvcTest", "DataJpaTest" -> "TEST_FRAMEWORK";
            default -> "ANNOTATION";
        };
    }
}
