package com.boyce.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Mojo(name = "obfuscate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class ConfigObfuscatorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * List of configuration keys to obfuscate.
     */
    @Parameter(property = "obfuscate.keys")
    private List<String> keys;

    /**
     * File patterns to include.
     */
    @Parameter(property = "obfuscate.includes")
    private List<String> includes;

    /**
     * File patterns to exclude.
     */
    @Parameter(property = "obfuscate.excludes")
    private List<String> excludes;

    /**
     * Prefix for obfuscated values (e.g., "ENC(").
     */
    @Parameter(property = "obfuscate.prefix", defaultValue = "-")
    private String prefix;

    /**
     * Suffix for obfuscated values (e.g., ")").
     */
    @Parameter(property = "obfuscate.suffix", defaultValue = "-")
    private String suffix;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (keys == null || keys.isEmpty()) {
            getLog().info("No keys configured for obfuscation. Skipping.");
            return;
        }

        if (includes == null || includes.isEmpty()) {
            includes = Arrays.asList(
                    "**/*.properties",
                    "**/*.yml",
                    "**/*.yaml",
                    "**/*.json",
                    "**/*.xml"
            );
        }

        File outputDirectory = new File(project.getBuild().getOutputDirectory());
        if (!outputDirectory.exists()) {
            getLog().info("Output directory does not exist: " + outputDirectory.getAbsolutePath());
            return;
        }

        try {
            processDirectory(outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Error processing configuration files", e);
        }
    }

    private void processDirectory(File directory) throws IOException {
        Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (shouldProcess(file)) {
                    getLog().info("Processing file: " + file);
                    processFile(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean shouldProcess(Path file) {
        Path outputDir = Paths.get(project.getBuild().getOutputDirectory());
        Path relativePath = outputDir.relativize(file);
        String relativePathString = relativePath.toString().replace(File.separator, "/");
        
        getLog().debug("Checking file: " + relativePathString);

        boolean included = false;
        for (String pattern : includes) {
            String normalizedPattern = pattern.replace(File.separator, "/");
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
            if (matcher.matches(relativePath)) {
                included = true;
                break;
            }
            // Manual fallback for ** patterns if glob fails for root files
            if (normalizedPattern.startsWith("**/")) {
                 String suffix = normalizedPattern.substring(3); // Remove **/
                 // Try matching the suffix as a glob against the filename itself if it's a root file
                 if (relativePath.getNameCount() == 1) {
                     PathMatcher suffixMatcher = FileSystems.getDefault().getPathMatcher("glob:" + suffix);
                     if (suffixMatcher.matches(relativePath)) {
                         included = true;
                         break;
                     }
                 }
            }
        }
        
        if (!included) return false;


        if (excludes != null) {
            for (String pattern : excludes) {
                String normalizedPattern = pattern.replace(File.separator, "/");
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
                if (matcher.matches(relativePath)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private void processFile(File file) throws IOException {
        String name = file.getName();
        if (name.endsWith(".properties")) {
            processProperties(file);
        } else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            processYaml(file);
        } else if (name.endsWith(".json")) {
            processJson(file);
        } else if (name.endsWith(".xml")) {
            processXml(file);
        }
    }

    private void processJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(file);

        if (processJsonNode(rootNode, "")) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);
        }
    }

    private boolean processJsonNode(JsonNode node, String parentKey) {
        boolean modified = false;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;
                JsonNode value = field.getValue();

                if (value.isObject()) {
                    if (processJsonNode(value, fullKey)) {
                        modified = true;
                    }
                } else if (value.isValueNode() && keys.contains(fullKey)) {
                     if (value.isTextual()) { // Only obfuscate text values
                         ((ObjectNode) node).put(key, obfuscate(value.asText()));
                         modified = true;
                         getLog().info("Obfuscated key: " + fullKey);
                     } else if (value.isNumber() || value.isBoolean()) {
                         // Convert non-string to string for obfuscation? Usually secrets are strings.
                         // Let's assume we force it to string if obfuscated.
                         ((ObjectNode) node).put(key, obfuscate(value.asText()));
                         modified = true;
                         getLog().info("Obfuscated key: " + fullKey);
                     }
                }
            }
        }
        return modified;
    }

    private void processXml(File file) throws IOException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();

            boolean modified = processXmlNode(doc.getDocumentElement(), "");

            if (modified) {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                // Optional: set output properties for better formatting if needed, though default DOM might suffice or be ugly.
                // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(file);
                transformer.transform(source, result);
            }
        } catch (Exception e) {
            throw new IOException("Error processing XML file: " + file.getName(), e);
        }
    }

    private boolean processXmlNode(Node node, String parentKey) {
        boolean modified = false;
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String key = child.getNodeName();
                String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;
                
                // Check if this element is a leaf node (has only text content)
                if (isLeafNode(child)) {
                    if (keys.contains(fullKey)) {
                        String originalValue = child.getTextContent();
                        if (originalValue != null && !originalValue.trim().isEmpty()) {
                            child.setTextContent(obfuscate(originalValue));
                            modified = true;
                            getLog().info("Obfuscated key: " + fullKey);
                        }
                    }
                } else {
                    // Recurse
                    if (processXmlNode(child, fullKey)) {
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    private boolean isLeafNode(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
        }
        return true;
    }


    private void processProperties(File file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        }

        boolean modified = false;
        for (String key : keys) {
            if (props.containsKey(key)) {
                String originalValue = props.getProperty(key);
                String obfuscatedValue = obfuscate(originalValue);
                props.setProperty(key, obfuscatedValue);
                modified = true;
                getLog().info("Obfuscated key: " + key);
            }
        }

        if (modified) {
            try (OutputStream out = new FileOutputStream(file)) {
                props.store(out, "Obfuscated by ConfigObfuscatorMavenPlugin");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processYaml(File file) throws IOException {
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        Map<String, Object> obj;
        try (InputStream in = new FileInputStream(file)) {
            obj = yaml.load(in);
        }

        if (obj == null) return;

        boolean modified = processYamlMap(obj, "");

        if (modified) {
            try (Writer writer = new FileWriter(file)) {
                yaml.dump(obj, writer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean processYamlMap(Map<String, Object> map, String parentKey) {
        boolean modified = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;
            Object value = entry.getValue();

            if (value instanceof Map) {
                if (processYamlMap((Map<String, Object>) value, fullKey)) {
                    modified = true;
                }
            } else if (keys.contains(fullKey)) {
                if (value != null) {
                    entry.setValue(obfuscate(value.toString()));
                    modified = true;
                    getLog().info("Obfuscated key: " + fullKey);
                }
            }
        }
        return modified;
    }

    private String obfuscate(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return prefix + "OBFUSCATED" + suffix;
    }
}
