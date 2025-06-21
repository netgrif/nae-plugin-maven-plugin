package com.netgrif.maven.plugin.module;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * <h2>Netgrif Plugin Registration Generator (Maven Mojo)</h2>
 *
 * <p>
 * This Maven Mojo generates plugin registration classes and configurations for the Netgrif platform.
 * It scans for plugin entry points in the specified package(s), generates registration configuration
 * Java classes, and sets up Spring auto-configuration imports to integrate the plugin at build time.
 * </p>
 *
 * <h3>Typical Use Cases</h3>
 * <ul>
 *     <li>Automated discovery and registration of annotated plugin entry points.</li>
 *     <li>Generation of metadata and configuration classes for seamless Netgrif platform integration.</li>
 *     <li>Automatic exposure of plugin API methods for runtime reflection/invocation.</li>
 * </ul>
 *
 * <h3>Example POM Usage</h3>
 * <pre>{@code
 * <plugin>
 *   <groupId>com.netgrif</groupId>
 *   <artifactId>nae-plugin-maven-plugin</artifactId>
 *   <version>1.0.0</version>
 *   <configuration>
 *     <entryPointsPackage>org.example.myplugin.entrypoints</entryPointsPackage>
 *     <generatedPackage>org.example.myplugin.generated</generatedPackage>
 *     <componentScanBasePackage>org.example.myplugin</componentScanBasePackage>
 *     <registrationName>my-custom-plugin</registrationName>
 *     <apiVersion>1.0.0</apiVersion>
 *   </configuration>
 * </plugin>
 * }</pre>
 *
 * <h3>Configuration Parameters</h3>
 * <ul>
 *     <li><b>entryPointsPackage</b> (required): Base Java package to scan for plugin entry points (annotated classes).</li>
 *     <li><b>generatedPackage</b> (optional): Target package for generated registration/configuration code.</li>
 *     <li><b>componentScanBasePackage</b> (optional): Spring component scan base for the generated config class.</li>
 *     <li><b>registrationName</b> (optional): Custom plugin registration name; defaults to artifactId if unset.</li>
 *     <li><b>apiVersion</b> (optional): API version this plugin targets; defaults to project version if unset.</li>
 * </ul>
 *
 * <h3>Output</h3>
 * <ul>
 *     <li>Java configuration class: {@code PluginRegistrationConfigurationImpl} (in the generated package)</li>
 *     <li>Spring auto-configuration imports: {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}</li>
 *     <li>Metadata map (key: Netgrif-*, value: project details), e.g. plugin name, version, SCM info, organization, license, build time, authors.</li>
 * </ul>
 *
 * <h3>Developer Notes</h3>
 * <ul>
 *     <li>Authors are only extracted from the current project's {@code <developers>} section, <b>never inherited from parent POMs</b>.</li>
 *     <li>License is composed from name + url, but only if set in the current project POM.</li>
 *     <li>The plugin will not generate registration code if no entry points are found in the configured package.</li>
 * </ul>
 *
 * <h3>Fields</h3>
 * <ul>
 *     <li>project: The Maven project being built (injected by Maven).</li>
 *     <li>entryPointsPackage: The Java package to scan for plugin entry points.</li>
 *     <li>generatedPackage: The package for generated Java code (may be autodetected).</li>
 *     <li>componentScanBasePackage: Spring base package for component scanning.</li>
 *     <li>name, version: (legacy, not typically used).</li>
 *     <li>registrationName: Name used for plugin registration (see above).</li>
 *     <li>apiVersion: Version of the API targeted by the plugin (see above).</li>
 * </ul>
 *
 * <h3>Main Methods</h3>
 * <ul>
 *     <li>execute: Main Mojo entry point (called by Maven).</li>
 *     <li>getRegistrationName: Resolves plugin registration name with fallback logic.</li>
 *     <li>getApiVersion: Resolves plugin API version with fallback logic.</li>
 *     <li>generatePluginRegistrationConfiguration: Scans entry points and generates configuration class.</li>
 *     <li>generateSpringAutoConfigurationImports: Sets up Spring auto-configuration metadata file.</li>
 *     <li>generatePluginRegistrationClassSource: Generates Java code for plugin config/metadata.</li>
 *     <li>buildNetgrifMetadata: Prepares metadata (including only explicit authors/licensing).</li>
 *     <li>putIfNotNull, escape: Utility methods for map population and string escaping.</li>
 * </ul>
 *
 * @author Netgrif team, plugin author
 * @since 1.0.0
 */
@Mojo(name = "generate-plugin-registration", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratePluginRegistrationMojo extends AbstractMojo {

    /**
     * Represents the Maven project for which this Mojo is executed.
     * <p>
     * This variable provides access to the Maven project information, such as
     * its build configuration, dependencies, properties, and output directories.
     * It is used throughout the plugin's execution lifecycle to retrieve and manipulate
     * project-related details, enabling the generation and configuration of certain
     * resources or classes.
     * <p>
     * As a Spring component, it is automatically managed and injected by the framework.
     */
    @Component
    private MavenProject project;

    /**
     * Specifies the base package to be scanned for discovering entry points in the plugin.
     * The discovered entry points are annotated classes that serve as extensions or integrations
     * with the application engine. The package scanning is typically used during the plugin
     * registration process to identify annotated classes and methods.
     * <p>
     * This property can be configured to narrow down the scanning scope, improving performance
     * and avoiding unnecessary checks in unrelated packages.
     * <p>
     * If not explicitly set, the entire classpath is scanned by default.
     */
    @Parameter(property = "entryPointsPackage")
    private String entryPointsPackage;

    /**
     * Specifies the Java package in which the generated plugin registration classes should be created.
     * If this value is null or blank, the package is auto-detected during the generation process
     * from the entry point classes within the project. If auto-detection fails, a default package
     * (`com.netgrif.generated.plugin`) is used.
     * <p>
     * This variable is utilized during the generation of the `PluginRegistrationConfigurationImpl` class
     * and affects the structure of the generated source files within the build directory.
     */
    @Parameter(property = "generatedPackage")
    private String generatedPackage;

    /**
     * Defines the base package to be used for component scanning during the generation
     * of the Spring plugin registration configuration. If not explicitly set, the base
     * package will be determined from detected entry points or defaults to a fallback package.
     * <p>
     * This property is typically provided as a Maven parameter and may influence
     * the scanning of annotated classes for plugin registration purposes.
     */
    @Parameter(property = "componentScanBasePackage")
    private String componentScanBasePackage;

    /**
     * Represents the name of the plugin being generated or processed.
     * This property can be configured via Maven by specifying the "name" parameter in the plugin configuration.
     * If not explicitly set, it may be derived or defaulted as needed during execution.
     */
    @Parameter(property = "name")
    private String name;

    /**
     * Represents the version of the plugin being processed or generated.
     * This variable typically holds the version information of the artifact,
     * which can either be explicitly set via the Maven parameter `version` or derived
     * from project properties or other inputs.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * Represents the name used for plugin registration within the Maven plugin.
     * This name is utilized in the generation of plugin registration configurations
     * and acts as an identifier for the plugin during runtime.
     * <p>
     * The value of this field can be directly specified via the Maven property
     * "registrationName". If not set explicitly, the value defaults to the artifact ID
     * of the current Maven project.
     * <p>
     * Usage:
     * - Primarily used in the generation of the `PluginRegistrationConfigurationImpl`
     * class, which includes crucial metadata for the plugin.
     * - Supports dynamic configuration through Maven properties or project metadata.
     */
    @Parameter(property = "registrationName")
    private String registrationName;

    /**
     * Represents the version of the API that the plugin targets.
     * The value can be provided through a Maven property with the key "apiVersion".
     * If not explicitly specified, the version is determined using the following fallback order:
     * 1. The value of the "apiVersion" property from the Maven project properties.
     * 2. The version of the Maven project as specified in its configuration.
     * Used to configure the plugin registration process and associate it with a specific API version.
     */
    @Parameter(property = "apiVersion")
    private String apiVersion;

    static class MethodDTO {
        String name;
        List<String> argTypes;
        String returnType;

        MethodDTO(String name, List<String> argTypes, String returnType) {
            this.name = name;
            this.argTypes = argTypes;
            this.returnType = returnType;
        }
    }

    static class EntryPointDTO {
        String name;
        String pluginName;
        Map<String, MethodDTO> methods;

        EntryPointDTO(String name, String pluginName, Map<String, MethodDTO> methods) {
            this.name = name;
            this.pluginName = pluginName;
            this.methods = methods;
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        try {
            String fqcn = generatePluginRegistrationConfiguration();
            if (fqcn != null) {
                generateSpringAutoConfigurationImports(fqcn);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot generate PluginRegistrationConfigurationImpl", e);
        }
    }

    /**
     * Retrieves the registration name for the plugin.
     * The method follows a fallback mechanism to determine the registration name:
     * 1. It first attempts to use the value of the `registrationName` field.
     * 2. If the `registrationName` is null or blank, it fetches the value of the "registrationName"
     * property from the project's properties.
     * 3. If the property value is still null or blank, it falls back to using the artifact ID.
     *
     * @return the determined registration name as a String. It will never return a blank string.
     */
    private String getRegistrationName() {
        String result = this.registrationName;
        if (result == null || result.isBlank()) {
            result = project.getProperties().getProperty("registrationName");
        }
        if (result == null || result.isBlank()) {
            result = project.getArtifactId();
        }
        return result;
    }

    /**
     * Retrieves the API version used in the plugin configuration.
     * If the `apiVersion` field is not set or is blank, it attempts to retrieve the version from the project's properties.
     * If the project's properties do not contain the API version, it falls back to the project's version.
     *
     * @return the resolved API version as a {@code String}. If none is explicitly set, it returns the project's version.
     */
    private String getApiVersion() {
        String result = this.apiVersion;
        if (result == null || result.isBlank()) {
            result = project.getProperties().getProperty("apiVersion");
        }
        if (result == null || result.isBlank()) {
            result = project.getVersion();
        }
        return result;
    }

    /**
     * Generates a fully qualified class name of the PluginRegistrationConfigurationImpl by scanning for annotated
     * entry points in the specified output directory, generating a Java class file for plugin registration, and
     * adding the generated source directory to the project's source roots. This method detects packages, processes
     * annotations, and constructs necessary metadata for entry points and methods.
     *
     * @return The fully qualified class name (FQCN) of the generated PluginRegistrationConfigurationImpl, or null
     * if no entry points are found during the scanning process.
     * @throws IOException If an I/O error occurs during directory creation or file writing operations.
     */
    private String generatePluginRegistrationConfiguration() throws IOException {
        String className = "PluginRegistrationConfigurationImpl";
        String detectedPackage = (generatedPackage != null && !generatedPackage.isBlank()) ? generatedPackage : null;
        String generatedSourceDir = project.getBuild().getDirectory() + "/generated-sources/plugin";

        String pluginNameValue = getRegistrationName();
        String pluginVersionValue = getApiVersion();

        getLog().info("Using pluginName = " + pluginNameValue + ", version = " + pluginVersionValue);

        Map<String, EntryPointDTO> entryPoints = new LinkedHashMap<>();
        List<ClassInfo> entryPointClasses = new ArrayList<>();

        getLog().info("Scanning for entry points in output directory: " + project.getBuild().getOutputDirectory());

        try (ScanResult scanResult = new ClassGraph()
                .overrideClasspath(project.getBuild().getOutputDirectory())
                .enableAnnotationInfo()
                .enableClassInfo()
                .enableMethodInfo()
                .acceptPackages(entryPointsPackage == null ? "" : entryPointsPackage)
                .scan()) {

            List<String> foundEntryPoints = new ArrayList<>();

            for (ClassInfo ci : scanResult.getClassesWithAnnotation("com.netgrif.application.engine.adapter.spring.plugin.annotations.EntryPoint")) {
                entryPointClasses.add(ci);
                String entryPointName = ci.getAnnotationInfo("com.netgrif.application.engine.adapter.spring.plugin.annotations.EntryPoint")
                        .getParameterValues().getValue("value").toString();

                getLog().info("Found @EntryPoint: " + ci.getName() + " (entryPointName = \"" + entryPointName + "\", package = " + ci.getPackageName() + ")");

                Map<String, MethodDTO> methods = new LinkedHashMap<>();

                for (MethodInfo mi : ci.getDeclaredMethodInfo()) {
                    if (mi.hasAnnotation("com.netgrif.application.engine.adapter.spring.plugin.annotations.EntryPointMethod")) {
                        String methodName = mi.getAnnotationInfo("com.netgrif.application.engine.adapter.spring.plugin.annotations.EntryPointMethod")
                                .getParameterValues().getValue("name").toString();
                        List<String> argTypes = new ArrayList<>();
                        Arrays.stream(mi.getParameterInfo()).forEach(param -> argTypes.add(param.getTypeDescriptor().toString()));
                        String returnType = mi.getTypeDescriptor().getResultType().toString();

                        getLog().info("  Found @EntryPointMethod: " + mi.getName()
                                + "(name=\"" + methodName + "\", argTypes=" + argTypes + ", returnType=" + returnType + ")");

                        methods.put(methodName, new MethodDTO(methodName, argTypes, returnType));
                    }
                }
                foundEntryPoints.add(entryPointName);
                entryPoints.put(entryPointName, new EntryPointDTO(entryPointName, pluginNameValue, methods));
            }
            if (foundEntryPoints.isEmpty()) {
                getLog().warn("No entry points (@EntryPoint) found, skipping generation of PluginRegistrationConfigurationImpl.");
                return null;
            } else {
                getLog().info("Total entry points found: " + foundEntryPoints.size() + " -> " + foundEntryPoints);
            }

            if ((detectedPackage == null || detectedPackage.isBlank()) && !entryPointClasses.isEmpty()) {
                detectedPackage = entryPointClasses.getFirst().getPackageName();
                getLog().info("Auto-detected generated package: " + detectedPackage);
            }
            if (detectedPackage == null || detectedPackage.isBlank()) {
                detectedPackage = "com.netgrif.generated.plugin";
                getLog().warn("Falling back to default package: " + detectedPackage);
            }
        }

        if (componentScanBasePackage == null || componentScanBasePackage.isBlank()) {
            componentScanBasePackage = detectedPackage;
        }

        String targetDir = generatedSourceDir + "/" + detectedPackage.replace(".", "/");
        Files.createDirectories(Paths.get(targetDir));

        String javaClass = generatePluginRegistrationClassSource(
                detectedPackage, className, pluginNameValue, pluginVersionValue, entryPoints
        );

        Path javaFile = Paths.get(targetDir, className + ".java");
        Files.writeString(javaFile, javaClass);

        project.addCompileSourceRoot(generatedSourceDir);

        String fqcn = detectedPackage + "." + className;
        getLog().info("Generated PluginRegistrationConfigurationImpl: " + javaFile + " as " + fqcn);
        return fqcn;
    }

    /**
     * Generates Spring Boot auto-configuration imports by creating a metadata file
     * in the `META-INF/spring` directory inside the project's build output directory.
     * The file `AutoConfiguration.imports` is populated with the fully qualified
     * class name (FQCN) provided as a parameter.
     *
     * @param fqcn the fully qualified class name to be included in the
     *             Spring Boot auto-configuration imports file
     * @throws IOException if an I/O error occurs while creating or writing to the file
     */
    private void generateSpringAutoConfigurationImports(String fqcn) throws IOException {
        String outputDir = project.getBuild().getOutputDirectory();
        File springMetaInf = new File(outputDir, "META-INF/spring");
        if (!springMetaInf.exists()) {
            springMetaInf.mkdirs();
        }
        File importsFile = new File(springMetaInf, "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        getLog().info("Adding to imports: " + fqcn);
        try (PrintWriter writer = new PrintWriter(new FileWriter(importsFile))) {
            writer.println(fqcn);
        }
        getLog().info("Generated " + importsFile.getAbsolutePath() + " with auto-configuration: " + fqcn);
    }

    /**
     * Generates a unique, formatted registration name for the plugin by combining the group ID, artifact ID,
     * and version of the Maven project. The group ID and version are modified to replace '.' and '-' characters with '_'.
     *
     * @return A concatenated and formatted string representing the unique plugin registration name.
     */
    private String pluginRegistrationName() {
        return project.getGroupId().replace('.', '_') + "_" +
                project.getArtifactId() + "_" +
                project.getVersion().replace('.', '_').replace('-', '_');
    }

    /**
     * Generates the source code for a plugin registration class.
     * This dynamically constructs the source code for a Spring-based plugin registration configuration class,
     * including metadata and entry points for the plugin being registered.
     *
     * @param packageName the package name to be used for the generated class
     * @param className   the name of the generated class
     * @param pluginName  the name of the plugin
     * @param version     the version of the plugin
     * @param entryPoints a map of entry points where each entry consists of the entry point's name as the key and
     *                    the corresponding {@link EntryPointDTO} as the value, representing the details of the entry points
     * @return the generated Java source code as a String for the plugin registration class
     */
    private String generatePluginRegistrationClassSource(
            String packageName, String className, String pluginName, String version,
            Map<String, EntryPointDTO> entryPoints
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n")
                .append("import com.netgrif.application.engine.adapter.spring.plugin.config.PluginRegistrationConfiguration;\n")
                .append("import com.netgrif.application.engine.objects.plugin.domain.EntryPoint;\n")
                .append("import com.netgrif.application.engine.objects.plugin.domain.Method;\n")
                .append("import org.springframework.context.annotation.Configuration;\n")
                .append("import org.springframework.context.annotation.Profile;\n")
                .append("import java.util.Map;\n")
                .append("import java.util.LinkedHashMap;\n");
        if (componentScanBasePackage != null && !componentScanBasePackage.isBlank()) {
            sb.append("import org.springframework.context.annotation.ComponentScan;\n");
        }
        sb.append("\n");
        sb.append("@Configuration\n");
        if (componentScanBasePackage != null && !componentScanBasePackage.isBlank()) {
            sb.append("@ComponentScan(\"").append(componentScanBasePackage).append("\")\n");
        }
        sb.append("public class ").append(className).append(" implements PluginRegistrationConfiguration {\n\n")
                .append("    private final String pluginName = \"").append(pluginName).append("\";\n")
                .append("    private final String version = \"").append(version).append("\";\n")
                .append("    private final Map<String, EntryPoint> entryPoints = new LinkedHashMap<>();\n")
                .append("    private final Map<String, String> metadata = new LinkedHashMap<>();\n\n")
                .append("    public ").append(className).append("() {\n");

        entryPoints.forEach((epName, ep) -> {
            sb.append("\n        // EntryPoint: ").append(ep.name).append("\n");
            sb.append("        entryPoints.put(\"").append(ep.name).append("\", new EntryPoint(")
                    .append("\"").append(ep.name).append("\", Map.of(\n");

            Iterator<Map.Entry<String, MethodDTO>> mit = ep.methods.entrySet().iterator();
            while (mit.hasNext()) {
                Map.Entry<String, MethodDTO> mEntry = mit.next();
                MethodDTO m = mEntry.getValue();
                sb.append("                \"").append(m.name).append("\", new Method(\"")
                        .append(m.name).append("\", java.util.List.of(");
                for (int i = 0; i < m.argTypes.size(); i++) {
                    sb.append("\"").append(m.argTypes.get(i)).append("\"");
                    if (i < m.argTypes.size() - 1) sb.append(", ");
                }
                sb.append("), \"").append(m.returnType).append("\", java.util.List.of())");
                if (mit.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("        ), \"").append(ep.pluginName).append("\"));\n");
        });

        sb.append("\n\n        // METADATA:\n");

        Map<String, String> meta = buildNetgrifMetadata();
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            sb.append("        metadata.put(\"").append(escape(entry.getKey())).append("\", \"").append(escape(entry.getValue())).append("\");\n");
        }

        sb.append("    }\n\n")
                .append("    @Override public String getPluginName() { return this.pluginName; }\n")
                .append("    @Override public String getVersion() { return this.version; }\n")
                .append("    @Override public Map<String, EntryPoint> getEntryPoints() { return this.entryPoints; }\n")
                .append("    @Override public Map<String, String> getMetadata() { return this.metadata; }\n")
                .append("}\n");
        return sb.toString();
    }

    /**
     * Builds metadata information for a Netgrif application, including details such as name, version, URL,
     * description, group ID, artifact ID, SCM information, license details, organization information, issue
     * management details, build JDK, authors, and other relevant properties. This metadata can be used for
     * documentation, registration, or integration purposes.
     *
     * @return A map containing metadata properties and their corresponding values. The keys represent metadata
     * attributes (e.g., "Netgrif-Name", "Netgrif-Version"), and the values represent the corresponding
     * details extracted from the project or generated dynamically.
     */
    private Map<String, String> buildNetgrifMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();
        putIfNotNull(meta, "Netgrif-Name", project.getName());
        putIfNotNull(meta, "Netgrif-Version", project.getVersion());
        putIfNotNull(meta, "Netgrif-Url", project.getUrl());
        putIfNotNull(meta, "Netgrif-Description", project.getDescription());
        putIfNotNull(meta, "Netgrif-GroupId", project.getGroupId());
        putIfNotNull(meta, "Netgrif-ArtifactId", project.getArtifactId());

        if (project.getScm() != null) {
            putIfNotNull(meta, "Netgrif-SCM-Connection", project.getScm().getConnection());
            putIfNotNull(meta, "Netgrif-SCM-URL", project.getScm().getUrl());
        }

        if (project.getLicenses() != null && !project.getLicenses().isEmpty()) {
            org.apache.maven.model.License license = (org.apache.maven.model.License) project.getLicenses().getFirst();
            StringBuilder licenseValue = new StringBuilder();
            if (license.getName() != null && !license.getName().isBlank()) {
                licenseValue.append(license.getName());
            }
            if (license.getUrl() != null && !license.getUrl().isBlank()) {
                if (!licenseValue.isEmpty()) licenseValue.append(" - ");
                licenseValue.append(license.getUrl());
            }
            if (!licenseValue.isEmpty()) {
                putIfNotNull(meta, "Netgrif-License", licenseValue.toString());
            }
        }

        if (project.getOrganization() != null) {
            putIfNotNull(meta, "Netgrif-Organization", project.getOrganization().getName());
            putIfNotNull(meta, "Netgrif-OrganizationUrl", project.getOrganization().getUrl());
        }

        if (project.getIssueManagement() != null) {
            putIfNotNull(meta, "Netgrif-IssueSystem", project.getIssueManagement().getSystem());
            putIfNotNull(meta, "Netgrif-IssueUrl", project.getIssueManagement().getUrl());
        }

        putIfNotNull(meta, "Netgrif-BuildJdk", System.getProperty("java.version"));
        meta.put("Netgrif-BuildTime", java.time.ZonedDateTime.now().toString());


        List<org.apache.maven.model.Developer> developers =
                (project.getOriginalModel() != null && project.getOriginalModel().getDevelopers() != null)
                        ? project.getOriginalModel().getDevelopers()
                        : Collections.emptyList();

        String authors = developers.stream()
                .map(dev -> {
                    String name = dev.getName();
                    String email = dev.getEmail();
                    String org = dev.getOrganization();
                    if (name == null || name.isBlank()) return null;
                    StringBuilder sb = new StringBuilder(name);
                    if (org != null && !org.isBlank()) sb.append(" (").append(org).append(")");
                    if (email != null && !email.isBlank()) sb.append(" <").append(email).append(">");
                    return sb.toString();
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n"));

        if (!authors.isBlank()) {
            putIfNotNull(meta, "Netgrif-Author", authors);
        }
        putIfNotNull(meta, "Netgrif-RegistrationName", getRegistrationName());
        putIfNotNull(meta, "Netgrif-ApiVersion", getApiVersion());

        return meta;
    }

    /**
     * Adds the given key-value pair to the provided map only if the value is not null
     * and is not blank (contains non-whitespace characters).
     *
     * @param map   the map to which the key-value pair should be added
     * @param key   the key to be added to the map
     * @param value the value associated with the key; if null or blank, no entry is added to the map
     */
    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * Escapes special characters in the provided string to ensure safe usage, replacing backslashes, quotes,
     * and newline or carriage return characters with their escaped equivalents.
     *
     * @param s the input string to escape; if null, an empty string is returned
     * @return the escaped version of the input string
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
