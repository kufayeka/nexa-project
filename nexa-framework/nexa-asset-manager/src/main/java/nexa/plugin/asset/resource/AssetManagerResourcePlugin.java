package nexa.plugin.asset.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.asset.dto.AssetDto;
import nexa.plugin.asset.dto.AssetWorkspaceDto;
import nexa.plugin.asset.dto.AttributeDto;
import nexa.plugin.asset.model.Asset;
import nexa.plugin.asset.model.AssetTemplate;
import nexa.plugin.asset.model.Attribute;
import nexa.plugin.asset.script.AssetScriptingEngine;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AssetManagerResourcePlugin implements NexaResourcePlugin {
    private final Asset rootAsset = new Asset("root", "/", null);
    private final ConcurrentHashMap<String, AssetTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attribute> flatAttributes = new ConcurrentHashMap<>();
    private final AssetScriptingEngine scriptingEngine = new AssetScriptingEngine(this);

    // Dependency tracking
    private final ConcurrentHashMap<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<String>> executionStack = ThreadLocal.withInitial(java.util.HashSet::new);

    private ScheduledExecutorService scheduler;
    private NexaPluginContext pluginContext;
    private String configFilePath;

    private static volatile AssetManagerResourcePlugin activeInstance;

    public static AssetManagerResourcePlugin getActiveInstance() {
        return activeInstance;
    }

    public AssetScriptingEngine getScriptingEngine() {
        return scriptingEngine;
    }

    @Override
    public String getPluginType() {
        return "asset-manager";
    }

    @Override
    public Object getNativeClient() {
        return this;
    }

    @Override
    public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        this.pluginContext = context;
        this.configFilePath = (String) config.get("configFile");
        if (this.configFilePath == null) {
            throw new IllegalArgumentException("Asset Manager requires 'configFile' parameter in its configuration.");
        }
        loadAssetWorkspace(configFilePath);
    }

    @Override
    public void onStart() throws Exception {
        activeInstance = this;

        // 1. Group attributes by interval for scheduling
        Map<Long, List<Attribute>> intervalGroups = new java.util.HashMap<>();
        for (Attribute attr : flatAttributes.values()) {
            if (attr.getCalculationConfig() != null && "INTERVAL".equals(attr.getCalculationConfig().triggerType())) {
                String intervalExpr = attr.getCalculationConfig().intervalExpr();
                if (intervalExpr != null && !intervalExpr.isBlank()) {
                    long ms = nexa.framework.runtime.domain.scheduler.helpers.DurationParser.parseWithMillisecondPrecision(intervalExpr).toMillis();
                    intervalGroups.computeIfAbsent(ms, k -> new ArrayList<>()).add(attr);
                }
            }
        }

        // 2. Start tick scheduler using virtual threads
        if (!intervalGroups.isEmpty()) {
            scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
            for (Map.Entry<Long, List<Attribute>> entry : intervalGroups.entrySet()) {
                long ms = entry.getKey();
                List<Attribute> attrs = entry.getValue();
                scheduler.scheduleAtFixedRate(() -> {
                    for (Attribute attr : attrs) {
                        Thread.startVirtualThread(() -> {
                            try {
                                recalculateAttribute(attr.getPath());
                            } catch (Exception e) {
                                System.err.println("Error running interval calculation for " + attr.getPath() + ": " + e.getMessage());
                            }
                        });
                    }
                }, ms, ms, TimeUnit.MILLISECONDS);
            }
        }

        // 3. Perform first-time evaluation on virtual threads to initialize values and discover dependencies
        for (Attribute attr : flatAttributes.values()) {
            if (attr.getCalculationConfig() != null) {
                String triggerType = attr.getCalculationConfig().triggerType();
                if ("ON_CHANGE".equals(triggerType) || "INTERVAL".equals(triggerType)) {
                    Thread.startVirtualThread(() -> {
                        try {
                            recalculateAttribute(attr.getPath());
                        } catch (Exception e) {
                            System.err.println("Initial calculation failed for " + attr.getPath() + ": " + e.getMessage());
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onStop() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        scriptingEngine.clearCompiledScripts();
    }

    public Asset getRootAsset() {
        return rootAsset;
    }

    public ConcurrentHashMap<String, AssetTemplate> getTemplates() {
        return templates;
    }

    public ConcurrentHashMap<String, Attribute> getFlatAttributes() {
        return flatAttributes;
    }

    public Object read(String attributePath) {
        Attribute attr = flatAttributes.get(normalizePath(attributePath));
        if (attr == null) {
            return null;
        }
        return attr.getValue();
    }

    public Map<String, Object> readVTQ(String attributePath) {
        Attribute attr = flatAttributes.get(normalizePath(attributePath));
        if (attr == null) {
            return null;
        }
        Object val = attr.getValue();
        Object oldVal = attr.getOldValue();
        return Map.of(
            "value", val != null ? val : "null",
            "oldValue", oldVal != null ? oldVal : "null",
            "timestamp", attr.getTimestamp(),
            "quality", attr.getQuality()
        );
    }

    public boolean write(String attributePath, Object value) {
        Attribute attr = flatAttributes.get(normalizePath(attributePath));
        if (attr == null) {
            return false;
        }
        if (scriptingEngine.isExecuting()) {
            throw new IllegalStateException("Direct write is prohibited inside attribute calculation scripts.");
        }

        Object finalVal = value;
        if (attr.getCalculationConfig() != null && "ON_WRITE".equals(attr.getCalculationConfig().triggerType())) {
            finalVal = scriptingEngine.executeCalculation(
                attr.getCalculationConfig().script(),
                attr.getPath(),
                attr.getValue(),
                attr.getOldValue(),
                value
            );
        }

        Object oldVal = attr.getValue();
        attr.updateValue(finalVal, "GOOD");

        notifyListeners(attr.getPath(), finalVal, oldVal, attr.getTimestamp(), attr.getQuality());

        if (!java.util.Objects.equals(oldVal, finalVal)) {
            triggerDependents(attr.getPath());
        }

        return true;
    }

    public void writeInternal(String attributePath, Object value, String quality) {
        Attribute attr = flatAttributes.get(normalizePath(attributePath));
        if (attr != null) {
            Object oldVal = attr.getValue();
            attr.updateValue(value, quality);
            notifyListeners(attr.getPath(), value, oldVal, attr.getTimestamp(), attr.getQuality());
            if (!java.util.Objects.equals(oldVal, value)) {
                triggerDependents(attr.getPath());
            }
        }
    }

    // Listener Registry
    public interface AttributeListener {
        void onUpdate(String path, Object value, Object oldValue, long timestamp, String quality);
    }

    private final ConcurrentHashMap<String, List<AttributeListener>> listeners = new ConcurrentHashMap<>();

    public void registerListener(String path, AttributeListener listener) {
        listeners.computeIfAbsent(normalizePath(path), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(listener);
    }

    public void unregisterListener(String path, AttributeListener listener) {
        List<AttributeListener> list = listeners.get(normalizePath(path));
        if (list != null) {
            list.remove(listener);
        }
    }

    private void notifyListeners(String path, Object value, Object oldValue, long timestamp, String quality) {
        List<AttributeListener> list = listeners.get(normalizePath(path));
        if (list != null) {
            for (AttributeListener l : list) {
                try {
                    l.onUpdate(path, value, oldValue, timestamp, quality);
                } catch (Exception e) {
                    System.err.println("Error notifying listener for " + path + ": " + e.getMessage());
                }
            }
        }
    }

    public void registerDependencies(String dependentPath, Set<String> readPaths) {
        if (readPaths == null) return;
        for (String readPath : readPaths) {
            dependencies.computeIfAbsent(normalizePath(readPath), k -> ConcurrentHashMap.newKeySet()).add(normalizePath(dependentPath));
        }
    }

    public void triggerDependents(String sourcePath) {
        String normSource = normalizePath(sourcePath);
        Set<String> deps = dependencies.get(normSource);
        if (deps == null || deps.isEmpty()) {
            return;
        }

        Set<String> stack = executionStack.get();
        for (String depPath : deps) {
            if (stack.contains(depPath)) {
                System.err.println("[Asset Manager Cycle Error] Recalculation cycle detected for: " + depPath);
                continue;
            }

            stack.add(depPath);
            try {
                recalculateAttribute(depPath);
            } finally {
                stack.remove(depPath);
            }
        }
    }

    private void recalculateAttribute(String attributePath) {
        Attribute attr = flatAttributes.get(attributePath);
        if (attr == null || attr.getCalculationConfig() == null) return;

        Object oldVal = attr.getValue();
        Object calculated = scriptingEngine.executeCalculation(
            attr.getCalculationConfig().script(),
            attr.getPath(),
            attr.getValue(),
            attr.getOldValue(),
            null
        );

        attr.updateValue(calculated, "GOOD");
        notifyListeners(attr.getPath(), calculated, oldVal, attr.getTimestamp(), attr.getQuality());

        if (!java.util.Objects.equals(oldVal, calculated)) {
            triggerDependents(attributePath);
        }
    }

    private void loadAssetWorkspace(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            file = new File("nexa-test/" + filePath);
        }
        if (!file.exists()) {
            file = new File(System.getProperty("user.dir"), filePath);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("Asset workspace file tidak ditemukan: " + filePath);
        }

        ObjectMapper mapper = new ObjectMapper();
        AssetWorkspaceDto wsDto = mapper.readValue(file, AssetWorkspaceDto.class);

        if (wsDto.templates() != null) {
            for (AssetTemplate t : wsDto.templates()) {
                templates.put(t.name(), t);
            }
        }

        if (wsDto.assets() != null) {
            for (AssetDto assetDto : wsDto.assets()) {
                instantiateAssetDto("/", assetDto);
            }
        }
    }

    private void instantiateAssetDto(String parentPath, AssetDto dto) {
        String name = dto.name();
        String path = parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
        path = normalizePath(path);

        Asset asset = new Asset(name, path, dto.template());

        if (dto.template() != null) {
            AssetTemplate template = templates.get(dto.template());
            if (template != null) {
                for (AssetTemplate.TemplateAttribute ta : template.attributes()) {
                    String taName = substituteParams(ta.name(), dto.parameters());
                    String dataType = ta.dataType();
                    Object taVal = ta.value();
                    if (taVal instanceof String strVal) {
                        taVal = substituteParams(strVal, dto.parameters());
                    }
                    Attribute.CalculationConfig calcConfig = null;
                    if (ta.calculationConfig() != null) {
                        String triggerType = ta.calculationConfig().triggerType();
                        String intervalExpr = ta.calculationConfig().intervalExpr();
                        String script = ta.calculationConfig().script();
                        script = substituteParams(script, dto.parameters());
                        calcConfig = new Attribute.CalculationConfig(triggerType, intervalExpr, script);
                    }

                    String attrPath = normalizePath(path + "/" + taName);
                    Attribute attr = new Attribute(taName, attrPath, dataType, taVal, calcConfig);
                    asset.getAttributes().put(taName, attr);
                    flatAttributes.put(attrPath, attr);
                }
            }
        }

        if (dto.attributes() != null) {
            for (AttributeDto attrDto : dto.attributes()) {
                String attrName = substituteParams(attrDto.name(), dto.parameters());
                Object val = attrDto.value();
                if (val instanceof String strVal) {
                    val = substituteParams(strVal, dto.parameters());
                }
                Attribute.CalculationConfig calcConfig = null;
                if (attrDto.calculationConfig() != null) {
                    String triggerType = attrDto.calculationConfig().triggerType();
                    String intervalExpr = attrDto.calculationConfig().intervalExpr();
                    String script = attrDto.calculationConfig().script();
                    script = substituteParams(script, dto.parameters());
                    calcConfig = new Attribute.CalculationConfig(triggerType, intervalExpr, script);
                }

                Attribute existing = asset.getAttributes().get(attrName);
                if (existing != null) {
                    if (val != null) {
                        existing.updateValue(val, "GOOD");
                    }
                    if (calcConfig != null) {
                        String attrPath = normalizePath(path + "/" + attrName);
                        Attribute overridden = new Attribute(attrName, attrPath, existing.getDataType(), val != null ? val : existing.getValue(), calcConfig);
                        asset.getAttributes().put(attrName, overridden);
                        flatAttributes.put(attrPath, overridden);
                    }
                } else {
                    String attrPath = normalizePath(path + "/" + attrName);
                    Attribute attr = new Attribute(attrName, attrPath, attrDto.dataType(), val, calcConfig);
                    asset.getAttributes().put(attrName, attr);
                    flatAttributes.put(attrPath, attr);
                }
            }
        }

        Asset parentAsset = findAssetByPath(parentPath);
        if (parentAsset != null) {
            parentAsset.getChildren().put(name, asset);
        } else if (parentPath.equals("/")) {
            rootAsset.getChildren().put(name, asset);
        }

        if (dto.children() != null) {
            for (AssetDto childDto : dto.children()) {
                instantiateAssetDto(path, childDto);
            }
        }
    }

    public Asset findAssetByPath(String path) {
        String normalized = normalizePath(path);
        if (normalized.equals("/")) {
            return rootAsset;
        }
        String[] parts = normalized.split("/");
        Asset current = rootAsset;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current = current.getChildren().get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String substituteParams(String template, Map<String, Object> parameters) {
        if (template == null || parameters == null || parameters.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    public static String normalizePath(String path) {
        if (path == null) return "/";
        String p = path.replace("\\", "/").trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        while (p.contains("//")) {
            p = p.replace("//", "/");
        }
        return p;
    }

    public static String resolvePath(String contextPath, String targetPath) {
        if (targetPath.startsWith("/")) {
            return normalizePath(targetPath);
        }

        String[] contextParts = contextPath.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : contextParts) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }

        String[] targetParts = targetPath.split("/");
        for (String targetPart : targetParts) {
            if (targetPart.isEmpty() || targetPart.equals(".")) {
                continue;
            }
            if (targetPart.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else {
                parts.add(targetPart);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append("/").append(part);
        }
        return normalizePath(sb.toString());
    }
}
