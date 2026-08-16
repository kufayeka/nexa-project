package nexa.plugin.modbus.node;

import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaSinkPlugin;
import nexa.plugin.modbus.helper.ModbusDataConverter;
import nexa.plugin.modbus.manager.ModbusConnectionManager;
import nexa.plugin.modbus.manager.ModbusConnectionManager.ModbusConnection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ModbusSharedSinkPlugin implements NexaSinkPlugin {
    private ModbusConnection connection;
    private String connectionPool;
    private String nodeId;
    private int unitId;
    private String writeType;
    private int address;
    private int quantity;
    private String valueSource;
    private String dataType;
    private String endianness;
    private long timeoutMs;

    @Override
    public String getPluginType() {
        return "modbus-shared-sink";
    }

    @Override
    public void onInit(
            final String targetId,
            final Map<String, Object> config,
            final NexaPluginContext context) throws Exception {
        this.nodeId = targetId;
        this.connectionPool = (String) config.get("connectionPool");
        this.unitId = ((Number) config.getOrDefault("unitId", 1)).intValue();
        this.writeType = (String) config.getOrDefault("writeType", "MULTIPLE_REGISTERS");
        this.address = ((Number) config.getOrDefault("address", 0)).intValue();
        this.quantity = ((Number) config.getOrDefault("quantity", 1)).intValue();
        this.valueSource = (String) config.getOrDefault("valueSource", "payload.value");
        this.dataType = (String) config.getOrDefault("dataType", "RAW_INT");
        this.endianness = (String) config.getOrDefault("endianness", "ABCD");
        this.timeoutMs = ((Number) config.getOrDefault("timeout", 3000)).longValue();
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
    }

    @Override
    public void consume(RuntimeMessage msg) {
        try {
            Object rawValue = msg.readRawValue(valueSource);
            if (rawValue == null) {
                System.err.println("[Modbus Write Node Warning] valueSource '" + valueSource + "' is null, skipping write.");
                return;
            }

            CompletableFuture<Void> future;
            String upperWriteType = writeType.trim().toUpperCase();

            switch (upperWriteType) {
                case "SINGLE_COIL" -> {
                    boolean boolVal;
                    if (rawValue instanceof Boolean b) {
                        boolVal = b;
                    } else {
                        boolVal = Boolean.parseBoolean(rawValue.toString());
                    }
                    future = connection.submitWrite(unitId, address, 1,
                            master -> {
                                master.writeSingleCoil(unitId, address, boolVal);
                                return null;
                            });
                }
                case "MULTIPLE_COILS" -> {
                    boolean[] coils;
                    if (rawValue instanceof List<?> list) {
                        coils = new boolean[list.size()];
                        for (int i = 0; i < coils.length; i++) {
                            Object item = list.get(i);
                            coils[i] = item instanceof Boolean b ? b : Boolean.parseBoolean(item.toString());
                        }
                    } else if (rawValue instanceof Boolean b) {
                        coils = new boolean[] { b };
                    } else {
                        coils = new boolean[] { Boolean.parseBoolean(rawValue.toString()) };
                    }
                    future = connection.submitWrite(unitId, address, coils.length,
                            master -> {
                                master.writeMultipleCoils(unitId, address, coils);
                                return null;
                            });
                }
                case "SINGLE_REGISTER" -> {
                    int[] regs = ModbusDataConverter.encodeValue(rawValue, dataType, endianness, 1);
                    int regVal = regs[0];
                    future = connection.submitWrite(unitId, address, 1,
                            master -> {
                                master.writeSingleRegister(unitId, address, regVal);
                                return null;
                            });
                }
                case "MULTIPLE_REGISTERS" -> {
                    int[] regs = ModbusDataConverter.encodeValues(rawValue, dataType, endianness, quantity);
                    future = connection.submitWrite(unitId, address, regs.length,
                            master -> {
                                master.writeMultipleRegisters(unitId, address, regs);
                                return null;
                            });
                }
                default -> throw new IllegalArgumentException("Unsupported Modbus writeType: " + writeType);
            }

            // Await execution
            future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("[Modbus Write Node Error] Node: " + nodeId 
                    + " | Address: " + address + " | Error: " + e.getMessage());
        }
    }

    @Override
    public void onStop() {
        // No node-specific background thread for sink
    }
}
