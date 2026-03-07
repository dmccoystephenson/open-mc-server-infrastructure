package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service that dynamically discovers and provides information about the OMCSI repository.
 * <p>Scans the repository filesystem at startup to build a knowledge base from actual
 * README files, compose.yml, scripts, configuration files, and workflow definitions.
 * This ensures the information stays in sync with the repository without maintaining
 * a separate static file.</p>
 */
@Slf4j
@Service
public class RepositoryInfoService {

    @Value("${repository.root:}")
    private String repositoryRoot;

    private final ObjectMapper objectMapper;
    private Map<String, Object> repositoryInfo;

    public RepositoryInfoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void scanRepository() {
        if (repositoryRoot == null || repositoryRoot.isBlank()) {
            log.warn("Repository root path not configured (repository.root). Repository info will not be available.");
            repositoryInfo = Map.of("error",
                    "Repository root path is not configured. Set the repository.root property or REPOSITORY_ROOT environment variable.");
            return;
        }

        Path repoPath = Path.of(repositoryRoot);
        if (!Files.isDirectory(repoPath)) {
            log.warn("Repository root does not exist or is not a directory: {}", repositoryRoot);
            repositoryInfo = Map.of("error", "Repository root path does not exist: " + repositoryRoot);
            return;
        }

        log.info("Scanning repository at: {}", repositoryRoot);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("overview", buildOverview(repoPath));
        info.put("services", discoverServices(repoPath));
        info.put("getting_started", buildGettingStarted(repoPath));
        info.put("architecture", buildArchitecture(repoPath));
        info.put("scripts", discoverScripts(repoPath));
        info.put("configuration", buildConfiguration(repoPath));
        info.put("self_hosting", buildSelfHosting(repoPath));
        info.put("ci_cd", discoverCiCd(repoPath));
        info.put("topics", buildTopicsList());

        repositoryInfo = info;
        log.info("Repository scan complete with {} topics", repositoryInfo.size());
    }

    /**
     * Get repository information for a specific topic.
     *
     * @param topic the topic to query (e.g. "overview", "services", "getting_started").
     *              If null or blank, returns all repository information.
     * @return JSON string containing the requested repository information
     */
    public String getRepositoryInfo(String topic) {
        log.info("Getting repository info for topic: {}", topic == null ? "all" : topic);
        try {
            if (topic == null || topic.isBlank()) {
                return objectMapper.writeValueAsString(repositoryInfo);
            }

            String normalizedTopic = topic.trim().toLowerCase().replace(" ", "_").replace("-", "_");
            Object topicData = repositoryInfo.get(normalizedTopic);

            if (topicData != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("topic", normalizedTopic);
                result.put("data", topicData);
                return objectMapper.writeValueAsString(result);
            }

            // Topic not found — return available topics
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "Topic '" + topic + "' not found.");
            result.put("available_topics", repositoryInfo.get("topics"));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize repository info for topic '{}': {}", topic, e.getMessage(), e);
            return "{\"error\":\"Failed to retrieve repository information.\"}";
        }
    }

    // ─── Topic Builders ─────────────────────────────────────────────────────

    private Map<String, Object> buildOverview(Path repoPath) {
        Map<String, Object> overview = new LinkedHashMap<>();
        try {
            Path readme = repoPath.resolve("README.md");
            if (Files.isRegularFile(readme)) {
                String content = readFileSafely(readme, 4000);
                if (content != null) {
                    String title = extractMarkdownTitle(content);
                    if (title != null) overview.put("name", title);
                    String description = extractFirstParagraph(content);
                    if (description != null) overview.put("description", description);
                }
            }

            Path license = repoPath.resolve("LICENSE");
            if (Files.isRegularFile(license)) {
                String licText = readFileSafely(license, 500);
                if (licText != null) {
                    if (licText.contains("MIT")) overview.put("license", "MIT");
                    else if (licText.contains("Apache")) overview.put("license", "Apache 2.0");
                    else overview.put("license", "See LICENSE file");
                }
            }

            List<String> docs = new ArrayList<>();
            for (String f : List.of("README.md", "CONTRIBUTING.md", "SELF-HOSTING.md",
                    "UPGRADE-GUIDE.md", "CI-DOCUMENTATION.md")) {
                if (Files.isRegularFile(repoPath.resolve(f))) docs.add(f);
            }
            if (!docs.isEmpty()) overview.put("documentation_files", docs);
        } catch (Exception e) {
            log.warn("Error building overview: {}", e.getMessage());
        }
        return overview;
    }

    private Map<String, Object> discoverServices(Path repoPath) {
        Map<String, Object> services = new LinkedHashMap<>();
        try {
            Map<String, Map<String, Object>> composeServices = parseComposeServices(repoPath);

            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(repoPath, Files::isDirectory)) {
                for (Path dir : dirs) {
                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith(".")) continue;

                    boolean hasDockerfile = Files.isRegularFile(dir.resolve("Dockerfile"));
                    boolean hasBuildGradle = Files.isRegularFile(dir.resolve("build.gradle"));
                    boolean hasReadme = Files.isRegularFile(dir.resolve("README.md"));

                    if (!hasDockerfile && !hasBuildGradle && !hasReadme) continue;

                    Map<String, Object> svcInfo = new LinkedHashMap<>();
                    if (hasReadme) {
                        String content = readFileSafely(dir.resolve("README.md"), 2000);
                        if (content != null) {
                            String desc = extractFirstParagraph(content);
                            if (desc != null) svcInfo.put("description", desc);
                        }
                    }
                    if (hasBuildGradle) svcInfo.put("build_tool", "Gradle");
                    if (hasDockerfile) svcInfo.put("containerized", true);

                    Map<String, Object> composeDef = findComposeService(composeServices, dirName);
                    if (composeDef != null) {
                        if (composeDef.containsKey("ports")) svcInfo.put("ports", composeDef.get("ports"));
                        if (composeDef.containsKey("container_name"))
                            svcInfo.put("container_name", String.valueOf(composeDef.get("container_name")));
                        Object dependsOn = composeDef.get("depends_on");
                        if (dependsOn != null) svcInfo.put("depends_on", dependsOn);
                    }

                    if (!svcInfo.isEmpty()) services.put(dirName, svcInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Error discovering services: {}", e.getMessage());
        }
        return services;
    }

    private Map<String, Object> buildGettingStarted(Path repoPath) {
        Map<String, Object> gettingStarted = new LinkedHashMap<>();
        try {
            List<String> quickStart = new ArrayList<>();
            if (Files.isRegularFile(repoPath.resolve("sample.env")))
                quickStart.add("Copy sample.env to .env and configure your settings: cp sample.env .env");
            if (Files.isRegularFile(repoPath.resolve("up.sh")))
                quickStart.add("Start all services: ./up.sh");
            if (Files.isRegularFile(repoPath.resolve("down.sh")))
                quickStart.add("Stop all services: ./down.sh");
            if (!quickStart.isEmpty()) gettingStarted.put("quick_start", quickStart);

            List<String> prerequisites = new ArrayList<>();
            if (Files.isRegularFile(repoPath.resolve("compose.yml")) ||
                    Files.isRegularFile(repoPath.resolve("docker-compose.yml")))
                prerequisites.add("Docker and Docker Compose installed");
            if (!prerequisites.isEmpty()) gettingStarted.put("prerequisites", prerequisites);
        } catch (Exception e) {
            log.warn("Error building getting_started: {}", e.getMessage());
        }
        return gettingStarted;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildArchitecture(Path repoPath) {
        Map<String, Object> architecture = new LinkedHashMap<>();
        try {
            Path composeFile = resolveComposeFile(repoPath);
            if (composeFile == null) {
                architecture.put("note", "No compose.yml found");
                return architecture;
            }

            try (InputStream is = Files.newInputStream(composeFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> compose = yaml.load(is);
                if (compose == null) return architecture;

                if (compose.get("services") instanceof Map<?, ?> svcSection) {
                    Map<String, Object> serviceSummaries = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : svcSection.entrySet()) {
                        String svcName = entry.getKey().toString();
                        if (entry.getValue() instanceof Map<?, ?> svcDef) {
                            Map<String, Object> svc = (Map<String, Object>) svcDef;
                            Map<String, Object> summary = new LinkedHashMap<>();
                            if (svc.containsKey("ports")) summary.put("ports", svc.get("ports"));
                            if (svc.containsKey("container_name"))
                                summary.put("container_name", String.valueOf(svc.get("container_name")));
                            if (svc.containsKey("depends_on")) summary.put("depends_on", svc.get("depends_on"));
                            if (svc.containsKey("volumes")) summary.put("volumes", svc.get("volumes"));
                            serviceSummaries.put(svcName, summary);
                        }
                    }
                    architecture.put("services", serviceSummaries);
                }

                if (compose.get("volumes") instanceof Map<?, ?> volSection) {
                    architecture.put("volumes", new ArrayList<>(((Map<String, Object>) volSection).keySet()));
                }
            }
        } catch (Exception e) {
            log.warn("Error building architecture: {}", e.getMessage());
        }
        return architecture;
    }

    private Map<String, Object> discoverScripts(Path repoPath) {
        Map<String, Object> scripts = new LinkedHashMap<>();
        try {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(repoPath, "*.sh")) {
                for (Path script : files) {
                    String name = script.getFileName().toString();
                    String desc = extractScriptDescription(script);
                    scripts.put(name, desc != null ? desc : "Shell script");
                }
            }

            Path scriptsDir = repoPath.resolve("scripts");
            if (Files.isDirectory(scriptsDir)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(scriptsDir, "*.sh")) {
                    for (Path script : files) {
                        String name = "scripts/" + script.getFileName().toString();
                        String desc = extractScriptDescription(script);
                        scripts.put(name, desc != null ? desc : "Shell script");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error discovering scripts: {}", e.getMessage());
        }
        return scripts;
    }

    private Map<String, Object> buildConfiguration(Path repoPath) {
        Map<String, Object> config = new LinkedHashMap<>();
        try {
            Path sampleEnv = repoPath.resolve("sample.env");
            if (Files.isRegularFile(sampleEnv)) {
                config.put("description",
                        "Configuration is managed through environment variables defined in a .env file. " +
                        "The sample.env file provides a template with all available options and their defaults.");
                String content = readFileSafely(sampleEnv, 5000);
                if (content != null) config.put("sample_env_content", content);
            }
        } catch (Exception e) {
            log.warn("Error building configuration: {}", e.getMessage());
        }
        return config;
    }

    private Map<String, Object> buildSelfHosting(Path repoPath) {
        Map<String, Object> selfHosting = new LinkedHashMap<>();
        try {
            Path selfHostingMd = repoPath.resolve("SELF-HOSTING.md");
            if (Files.isRegularFile(selfHostingMd)) {
                String content = readFileSafely(selfHostingMd, 4000);
                if (content != null) selfHosting.put("content", content);
            } else {
                selfHosting.put("note", "No SELF-HOSTING.md found in the repository.");
            }
        } catch (Exception e) {
            log.warn("Error building self_hosting: {}", e.getMessage());
        }
        return selfHosting;
    }

    private Map<String, Object> discoverCiCd(Path repoPath) {
        Map<String, Object> ciCd = new LinkedHashMap<>();
        try {
            Path workflowsDir = repoPath.resolve(".github").resolve("workflows");
            if (Files.isDirectory(workflowsDir)) {
                List<Map<String, String>> workflows = new ArrayList<>();
                try (DirectoryStream<Path> files = Files.newDirectoryStream(workflowsDir, "*.yml")) {
                    for (Path wf : files) {
                        Map<String, String> wfInfo = new LinkedHashMap<>();
                        wfInfo.put("file", wf.getFileName().toString());
                        String content = readFileSafely(wf, 500);
                        if (content != null) {
                            String name = extractYamlName(content);
                            if (name != null) wfInfo.put("name", name);
                        }
                        workflows.add(wfInfo);
                    }
                }
                ciCd.put("platform", "GitHub Actions");
                ciCd.put("workflows", workflows);
            }
        } catch (Exception e) {
            log.warn("Error discovering CI/CD: {}", e.getMessage());
        }
        return ciCd;
    }

    private Map<String, Object> buildTopicsList() {
        return Map.of("available_topics", List.of(
                "overview - General project information",
                "services - Details about each microservice",
                "getting_started - Quick start guide and prerequisites",
                "architecture - System architecture and data flow",
                "scripts - Available utility scripts",
                "configuration - Environment variable configuration",
                "self_hosting - Self-hosting guide and network setup",
                "ci_cd - CI/CD pipeline information"
        ));
    }

    // ─── Compose Helpers ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> parseComposeServices(Path repoPath) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Path composeFile = resolveComposeFile(repoPath);
        if (composeFile == null) return result;

        try (InputStream is = Files.newInputStream(composeFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> compose = yaml.load(is);
            if (compose != null && compose.get("services") instanceof Map<?, ?> svcSection) {
                for (Map.Entry<?, ?> entry : svcSection.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> svcDef) {
                        result.put(entry.getKey().toString(), (Map<String, Object>) svcDef);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse compose file: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findComposeService(Map<String, Map<String, Object>> composeServices, String dirName) {
        // Direct name match
        if (composeServices.containsKey(dirName)) return composeServices.get(dirName);

        // Match by build context path
        for (Map.Entry<String, Map<String, Object>> entry : composeServices.entrySet()) {
            String context = extractBuildContext(entry.getValue());
            if (context != null) {
                String normalizedCtx = context.replace("./", "").replace("\\", "/");
                if (normalizedCtx.equals(dirName)) return entry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractBuildContext(Map<String, Object> serviceDef) {
        Object build = serviceDef.get("build");
        if (build instanceof String str) return str;
        if (build instanceof Map<?, ?> buildMap) {
            Object context = ((Map<String, Object>) buildMap).get("context");
            if (context instanceof String ctx) return ctx;
        }
        return null;
    }

    private Path resolveComposeFile(Path repoPath) {
        Path composeYml = repoPath.resolve("compose.yml");
        if (Files.isRegularFile(composeYml)) return composeYml;
        Path dockerComposeYml = repoPath.resolve("docker-compose.yml");
        if (Files.isRegularFile(dockerComposeYml)) return dockerComposeYml;
        return null;
    }

    // ─── Text Extraction Helpers ────────────────────────────────────────────

    private String readFileSafely(Path path, int maxChars) {
        try {
            String content = Files.readString(path);
            if (content.length() > maxChars) {
                return content.substring(0, maxChars) + "\n... (truncated)";
            }
            return content;
        } catch (IOException e) {
            log.warn("Failed to read file {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Extract the first Markdown heading (# Title) from content.
     */
    String extractMarkdownTitle(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) return trimmed.substring(2).trim();
        }
        return null;
    }

    /**
     * Extract the first non-heading paragraph from Markdown content.
     */
    String extractFirstParagraph(String content) {
        StringBuilder paragraph = new StringBuilder();
        boolean pastTitle = false;
        boolean collecting = false;

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            if (!pastTitle) {
                if (trimmed.startsWith("# ") || trimmed.isEmpty()) {
                    if (trimmed.startsWith("# ")) pastTitle = true;
                    continue;
                }
                pastTitle = true;
            }

            if (!collecting && trimmed.isEmpty()) continue;
            if (collecting && (trimmed.isEmpty() || trimmed.startsWith("#"))) break;

            collecting = true;
            if (!paragraph.isEmpty()) paragraph.append(" ");
            paragraph.append(trimmed);
        }

        String result = paragraph.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Extract the first meaningful comment from a shell script (skipping shebang).
     */
    private String extractScriptDescription(Path scriptPath) {
        try {
            List<String> lines = Files.readAllLines(scriptPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#!")) continue;
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#")) return trimmed.substring(1).trim();
                break;
            }
        } catch (IOException e) {
            log.warn("Failed to read script {}: {}", scriptPath, e.getMessage());
        }
        return null;
    }

    /**
     * Extract the 'name:' field from YAML content.
     */
    private String extractYamlName(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("name:")) {
                String value = trimmed.substring(5).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }
}
