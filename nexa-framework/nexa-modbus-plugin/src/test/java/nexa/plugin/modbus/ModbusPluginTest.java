package nexa.plugin.modbus;

import nexa.plugin.modbus.helper.ModbusDataConverter;
import nexa.plugin.modbus.manager.ModbusConnectionManager.ModbusTask;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class ModbusPluginTest {

    @Test
    public void testEndiannessSwaps() {
        // ABCD (Big Endian)
        byte[] raw = new byte[]{0x11, 0x22, 0x33, 0x44};
        byte[] abcd = ModbusDataConverter.applyEndianness(raw, "ABCD");
        assertArrayEquals(raw, abcd);

        // BADC (Byte Swap)
        byte[] badc = ModbusDataConverter.applyEndianness(raw, "BADC");
        assertArrayEquals(new byte[]{0x22, 0x11, 0x44, 0x33}, badc);

        // CDAB (Word Swap)
        byte[] cdab = ModbusDataConverter.applyEndianness(raw, "CDAB");
        assertArrayEquals(new byte[]{0x33, 0x44, 0x11, 0x22}, cdab);

        // DCBA (Little Endian / Double Swap)
        byte[] dcba = ModbusDataConverter.applyEndianness(raw, "DCBA");
        assertArrayEquals(new byte[]{0x44, 0x33, 0x22, 0x11}, dcba);
    }

    @Test
    public void testDataTypesEncodingDecoding() {
        // INT16
        int[] int16Regs = ModbusDataConverter.encodeValue((short) -12345, "INT16", "ABCD", 1);
        assertEquals(1, int16Regs.length);
        Object int16Decoded = ModbusDataConverter.decodeValue(int16Regs, "INT16", "ABCD");
        assertEquals((short) -12345, int16Decoded);

        // UINT16
        int[] uint16Regs = ModbusDataConverter.encodeValue(50000, "UINT16", "ABCD", 1);
        assertEquals(1, uint16Regs.length);
        Object uint16Decoded = ModbusDataConverter.decodeValue(uint16Regs, "UINT16", "ABCD");
        assertEquals(50000, uint16Decoded);

        // INT32
        int[] int32Regs = ModbusDataConverter.encodeValue(-123456789, "INT32", "CDAB", 2);
        assertEquals(2, int32Regs.length);
        Object int32Decoded = ModbusDataConverter.decodeValue(int32Regs, "INT32", "CDAB");
        assertEquals(-123456789, int32Decoded);

        // UINT32
        int[] uint32Regs = ModbusDataConverter.encodeValue(4000000000L, "UINT32", "DCBA", 2);
        assertEquals(2, uint32Regs.length);
        Object uint32Decoded = ModbusDataConverter.decodeValue(uint32Regs, "UINT32", "DCBA");
        assertEquals(4000000000L, uint32Decoded);

        // FLOAT32
        int[] floatRegs = ModbusDataConverter.encodeValue(123.45f, "FLOAT32", "BADC", 2);
        assertEquals(2, floatRegs.length);
        Object floatDecoded = ModbusDataConverter.decodeValue(floatRegs, "FLOAT32", "BADC");
        assertEquals(123.45f, (float) floatDecoded, 0.001f);

        // INT64 / LONG
        int[] int64Regs = ModbusDataConverter.encodeValue(-9876543210123L, "INT64", "ABCD", 4);
        assertEquals(4, int64Regs.length);
        Object int64Decoded = ModbusDataConverter.decodeValue(int64Regs, "INT64", "ABCD");
        assertEquals(-9876543210123L, int64Decoded);

        // UINT64
        BigInteger bigVal = new BigInteger("18446744073709551610"); // Close to max unsigned 64-bit int
        int[] uint64Regs = ModbusDataConverter.encodeValue(bigVal, "UINT64", "ABCD", 4);
        assertEquals(4, uint64Regs.length);
        Object uint64Decoded = ModbusDataConverter.decodeValue(uint64Regs, "UINT64", "ABCD");
        assertEquals(bigVal, uint64Decoded);

        // FLOAT64 / DOUBLE
        int[] doubleRegs = ModbusDataConverter.encodeValue(9876.54321, "FLOAT64", "DCBA", 4);
        assertEquals(4, doubleRegs.length);
        Object doubleDecoded = ModbusDataConverter.decodeValue(doubleRegs, "FLOAT64", "DCBA");
        assertEquals(9876.54321, (double) doubleDecoded, 0.000001);

        // STRING
        int[] stringRegs = ModbusDataConverter.encodeValue("NEXA", "STRING", "ABCD", 3);
        assertEquals(3, stringRegs.length);
        Object stringDecoded = ModbusDataConverter.decodeValue(stringRegs, "STRING", "ABCD");
        assertEquals("NEXA", stringDecoded);

        // RAW_HEX
        int[] hexRegs = ModbusDataConverter.encodeValue("0A0B0C0D", "RAW_HEX", "ABCD", 2);
        assertEquals(2, hexRegs.length);
        Object hexDecoded = ModbusDataConverter.decodeValue(hexRegs, "RAW_HEX", "ABCD");
        assertEquals("0A0B0C0D", hexDecoded);

        // RAW_INT
        List<Integer> listVal = new ArrayList<>();
        listVal.add(10);
        listVal.add(20);
        listVal.add(30);
        int[] rawRegs = ModbusDataConverter.encodeValue(listVal, "RAW_INT", "ABCD", 3);
        assertArrayEquals(new int[]{10, 20, 30}, rawRegs);
        Object rawDecoded = ModbusDataConverter.decodeValue(rawRegs, "RAW_INT", "ABCD");
        assertEquals(listVal, rawDecoded);
    }

    @Test
    public void testTaskPrioritization() {
        // Mock task implementation for comparison testing
        class TestModbusTask extends ModbusTask<Void> {
            protected TestModbusTask(int priority, long seqNum, int unitId, int address, boolean sortReadQueue) {
                super(priority, seqNum, unitId, address, 1, sortReadQueue);
            }

            @Override
            public Void run(com.intelligt.modbus.jlibmodbus.master.ModbusMaster master) {
                return null;
            }
        }

        // 1. Compare priorities: Write (1) should come before Read (2)
        TestModbusTask writeTask = new TestModbusTask(1, 100L, 1, 100, true);
        TestModbusTask readTask = new TestModbusTask(2, 50L, 1, 200, true);
        assertTrue(writeTask.compareTo(readTask) < 0, "Write task must prioritize over Read task");
        assertTrue(readTask.compareTo(writeTask) > 0);

        // 2. Compare same priorities (WRITE vs WRITE): FIFO sequence number
        TestModbusTask write1 = new TestModbusTask(1, 10L, 1, 200, true);
        TestModbusTask write2 = new TestModbusTask(1, 20L, 1, 100, true);
        assertTrue(write1.compareTo(write2) < 0, "Write same-priority tasks must be FIFO ordered");

        // 3. Compare same priorities (READ vs READ) with sortReadQueue = true: sort by Unit ID then Address
        TestModbusTask readId2 = new TestModbusTask(2, 100L, 2, 50, true);
        TestModbusTask readId1 = new TestModbusTask(2, 200L, 1, 1000, true);
        assertTrue(readId1.compareTo(readId2) < 0, "Read same-priority tasks must be sorted by UnitID first");

        TestModbusTask readAddr2 = new TestModbusTask(2, 300L, 1, 500, true);
        TestModbusTask readAddr1 = new TestModbusTask(2, 400L, 1, 200, true);
        assertTrue(readAddr1.compareTo(readAddr2) < 0, "Read same-priority tasks with same UnitID must be sorted by Address");

        // 4. Compare same priorities (READ vs READ) with sortReadQueue = false: FIFO sequence number
        TestModbusTask readNoSort1 = new TestModbusTask(2, 5L, 2, 50, false);
        TestModbusTask readNoSort2 = new TestModbusTask(2, 15L, 1, 1000, false);
        assertTrue(readNoSort1.compareTo(readNoSort2) < 0, "When sorting is disabled, Read tasks must be FIFO ordered");
    }
}
