package nexa.plugin.asset.model;

import java.util.concurrent.locks.ReentrantLock;

public final class Attribute {
    private final String name;
    private final String path;
    private final AssetDataType dataType;
    private final CalculationConfig calculationConfig;
    private final ReentrantLock lock = new ReentrantLock();

    private Object value;
    private Object oldValue;
    private Object newValue;
    private long timestamp;
    private String quality;

    public Attribute(String name, String path, String dataType, Object defaultValue, CalculationConfig calculationConfig) {
        this(name, path, AssetDataType.parse(dataType), defaultValue, calculationConfig);
    }

    public Attribute(String name, String path, AssetDataType dataType, Object defaultValue, CalculationConfig calculationConfig) {
        this.name = name;
        this.path = path;
        this.dataType = dataType;
        this.value = dataType.coerce(defaultValue);

        this.oldValue = null;
        this.newValue = null;
        this.timestamp = System.currentTimeMillis();
        this.quality = "GOOD";
        this.calculationConfig = calculationConfig;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getDataType() { return dataType.name(); }
    public AssetDataType getAssetDataType() { return dataType; }
    public AssetDataType dataType() { return dataType; }
    public CalculationConfig getCalculationConfig() { return calculationConfig; }

    public Object getValue() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }

    public Object getOldValue() {
        lock.lock();
        try {
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    public Object getNewValue() {
        lock.lock();
        try {
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    public long getTimestamp() {
        lock.lock();
        try {
            return timestamp;
        } finally {
            lock.unlock();
        }
    }

    public String getQuality() {
        lock.lock();
        try {
            return quality;
        } finally {
            lock.unlock();
        }
    }

    public void updateValue(Object val, String q) {
        lock.lock();
        try {
            this.oldValue = this.value;
            this.value = dataType.coerce(val);
            this.timestamp = System.currentTimeMillis();
            this.quality = q;
        } finally {
            lock.unlock();
        }
    }

    public void setNewValue(Object val) {
        lock.lock();
        try {
            this.newValue = dataType.coerce(val);
        } finally {
            lock.unlock();
        }
    }

    public static record CalculationConfig(
        String triggerType,
        String intervalExpr,
        String script
    ) {}
}
