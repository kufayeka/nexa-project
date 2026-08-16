package nexa.plugin.asset.model;

import java.util.concurrent.ConcurrentHashMap;

public final class Asset {
    private final String name;
    private final String path;
    private final String templateName; // Optional (null if no template)
    private final ConcurrentHashMap<String, Attribute> attributes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Asset> children = new ConcurrentHashMap<>();

    public Asset(String name, String path, String templateName) {
        this.name = name;
        this.path = path;
        this.templateName = templateName;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getTemplateName() { return templateName; }
    public ConcurrentHashMap<String, Attribute> getAttributes() { return attributes; }
    public ConcurrentHashMap<String, Asset> getChildren() { return children; }
}
