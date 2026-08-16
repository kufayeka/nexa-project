package nexa.plugin.modbus.resource;

import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.plugin.modbus.manager.ModbusConnectionManager;
import nexa.plugin.modbus.manager.ModbusConnectionManager.ModbusConnection;

import java.util.Map;

public final class ModbusConnectionPoolPlugin implements NexaResourcePlugin {
    private String targetId;
    private String name;
    private String host;
    private int port;
    private int timeout;
    private int interTransactionDelay;
    private String writePriorityMode;
    private int reconnectDelay;
    private boolean keepAlive;
    private boolean sortReadQueue;

    private ModbusConnection connection;

    @Override
    public String getPluginType() {
        return "modbus-connection-pool";
    }

    @Override
    public Object getNativeClient() {
        return this.connection;
    }

    @Override
    public void onInit(
            final String targetId,
            final Map<String, Object> config,
            final NexaPluginContext context) throws Exception {
        this.targetId = targetId;
        this.name = (String) config.get("name");
        this.host = (String) config.getOrDefault("host", "localhost");
        this.port = ((Number) config.getOrDefault("port", 502)).intValue();
        this.timeout = ((Number) config.getOrDefault("timeout", 2000)).intValue();
        this.interTransactionDelay = ((Number) config.getOrDefault("interTransactionDelay", 20)).intValue();
        this.writePriorityMode = (String) config.getOrDefault("writePriorityMode", "HIGH");
        this.reconnectDelay = ((Number) config.getOrDefault("reconnectDelay", 5000)).intValue();
        this.keepAlive = (Boolean) config.getOrDefault("keepAlive", true);
        this.sortReadQueue = (Boolean) config.getOrDefault("sortReadQueue", true);
    }

    @Override
    public void onStart() throws Exception {
        this.connection = ModbusConnectionManager.getOrCreateConnection(
                this.host,
                this.port,
                this.timeout,
                this.interTransactionDelay,
                this.writePriorityMode,
                this.reconnectDelay,
                this.keepAlive,
                this.sortReadQueue
        );

        ModbusConnectionManager.registerConnectionReference(
                this.targetId,
                this.name,
                this.connection
        );
    }

    @Override
    public void onStop() {
        ModbusConnectionManager.unregisterConnectionReference(this.targetId, this.name);
        ModbusConnectionManager.removeConnection(this.host, this.port);
    }
}
