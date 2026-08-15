package nexa.plugin.modbus.node;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.plugin.modbus.helper.ModbusDataConverter;
import nexa.plugin.modbus.manager.ModbusConnectionManager;
import nexa.plugin.modbus.manager.ModbusConnectionManager.ModbusConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ModbusSharedInputPlugin implements NexaSourcePlugin {
    private Consumer<RuntimeMessage> emitter;
    private ModbusConnection connection;
    private String connectionPool;
    private String nodeId;
    private int unitId;
    private String readType;
    private int address;
    private int quantity;
    private long pollIntervalMs;
    private String dataType;
    private String endianness;
    private String outputField;
    private boolean coalesce;
    private long timeoutMs;

    private volatile boolean running = false;
    private Thread pollingThread;

    @Override
    public String getPluginType() {
        return "modbus-shared-input";
    }

    @Override
    public void setEmitter(Consumer<RuntimeMessage> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onInit(
            final String targetId,
            final Map<String, Object> config,
            final NexaPluginContext context) throws Exception {
        this.nodeId = targetId;
        this.connectionPool = (String) config.get("connectionPool");
        this.unitId = ((Number) config.getOrDefault("unitId", 1)).intValue();
        this.readType = (String) config.getOrDefault("readType", "HOLDING_REGISTERS");
        this.address = ((Number) config.getOrDefault("address", 0)).intValue();
        this.quantity = ((Number) config.getOrDefault("quantity", 1)).intValue();
        this.dataType = (String) config.getOrDefault("dataType", "RAW_INT");
        this.endianness = (String) config.getOrDefault("endianness", "ABCD");
        this.outputField = (String) config.getOrDefault("outputField", "payload.value");
        this.coalesce = (Boolean) config.getOrDefault("coalesce", true);
        this.timeoutMs = ((Number) config.getOrDefault("timeout", 3000)).longValue();

        long parsedInterval = parseDuration(config.get("pollInterval"), 1000L);
        this.pollIntervalMs = Math.max(100, parsedInterval); // Minimum 100ms
    }

    @Override
    public void onStart() throws Exception {
        Object clientObj = ModbusConnectionManager.getConnectionByNameOrId(this.connectionPool);
        if (clientObj instanceof ModbusConnection conn) {
            this.connection = conn;
        }

        if (this.connection == null) {
            throw new IllegalStateException(
                    "Modbus Connection Pool tidak ditemukan atau belum terinisialisasi: "
                            + this.connectionPool);
        }

        this.running = true;
        this.pollingThread = Thread.startVirtualThread(this::runPollingLoop);
        System.out.println("[Modbus Input Node] Started polling node: " + nodeId 
                + " for address: " + address + " (Interval: " + pollIntervalMs + "ms)");
    }

    @Override
    public void onStop() {
        this.running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    private void runPollingLoop() {
        while (running) {
            try {
                long startTime = System.currentTimeMillis();

                // Submit read command as a lambda to Connection Executor Queue
                CompletableFuture<Object> future;
                String upperReadType = readType.trim().toUpperCase();

                switch (upperReadType) {
                    case "COILS" -> future = connection.submitRead(unitId, upperReadType, address, quantity, coalesce,
                            master -> master.readCoils(unitId, address, quantity));
                    case "DISCRETE_INPUTS" -> future = connection.submitRead(unitId, upperReadType, address, quantity, coalesce,
                            master -> master.readDiscreteInputs(unitId, address, quantity));
                    case "INPUT_REGISTERS" -> future = connection.submitRead(unitId, upperReadType, address, quantity, coalesce,
                            master -> master.readInputRegisters(unitId, address, quantity));
                    case "HOLDING_REGISTERS" -> future = connection.submitRead(unitId, upperReadType, address, quantity, coalesce,
                            master -> master.readHoldingRegisters(unitId, address, quantity));
                    default -> throw new IllegalArgumentException("Unsupported Modbus readType: " + readType);
                }

                try {
                    // Block virtual thread waiting for queue execution
                    Object rawResult = future.get(timeoutMs, TimeUnit.MILLISECONDS);

                    // Decode result
                    Object decodedVal;
                    if ("COILS".equals(upperReadType) || "DISCRETE_INPUTS".equals(upperReadType)) {
                        boolean[] bits = (boolean[]) rawResult;
                        if (quantity == 1) {
                            decodedVal = bits[0];
                        } else {
                            List<Boolean> bitList = new ArrayList<>(bits.length);
                            for (boolean b : bits) bitList.add(b);
                            decodedVal = bitList;
                        }
                    } else {
                        int[] regs = (int[]) rawResult;
                        decodedVal = ModbusDataConverter.decodeValue(regs, dataType, endianness);
                    }

                    // Emit to Nexa pipeline
                    RuntimeMessage msg = new RuntimeMessage();
                    msg.writeValue(outputField, decodedVal);
                    msg.writeValue("payload.unitId", unitId);
                    msg.writeValue("payload.address", address);
                    msg.writeValue("payload.timestamp", System.currentTimeMillis());

                    if (emitter != null) {
                        emitter.accept(msg);
                    }

                } catch (Exception ex) {
                    System.err.println("[Modbus Read Node Error] Node: " + nodeId 
                            + " | Address: " + address + " | Error: " + ex.getMessage());
                }

                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = Math.max(10, pollIntervalMs - elapsed);
                Thread.sleep(sleepTime);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Modbus Input Node Loop Exception] Node: " + nodeId + " | " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static long parseDuration(Object value, long fallbackMs) {
        if (value == null) return fallbackMs;
        if (value instanceof Number num) return num.longValue();
        String s = value.toString().trim().toLowerCase();
        if (s.isEmpty()) return fallbackMs;
        try {
            if (s.endsWith("ms")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim());
            } else if (s.endsWith("s")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 1000L;
            } else if (s.endsWith("m")) {
                return Long.parseLong(s.substring(0, s.length() - 1).trim()) * 60000L;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallbackMs;
        }
    }
}
