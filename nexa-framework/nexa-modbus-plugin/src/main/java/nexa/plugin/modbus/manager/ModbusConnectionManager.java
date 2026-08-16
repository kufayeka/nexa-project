package nexa.plugin.modbus.manager;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class ModbusConnectionManager {

    private static final ConcurrentHashMap<String, ModbusConnection> connectionPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ModbusConnection> lookupRegistry = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private ModbusConnectionManager() {
    }

    public static ModbusConnection getOrCreateConnection(
            String host,
            int port,
            int timeout,
            int interTransactionDelay,
            String writePriorityMode,
            int reconnectDelay,
            boolean keepAlive,
            boolean sortReadQueue) throws Exception {

        String key = host + ":" + port;
        ModbusConnection conn = connectionPool.get(key);

        if (conn == null) {
            lock.lock();
            try {
                conn = connectionPool.get(key);
                if (conn == null) {
                    conn = new ModbusConnection(
                            host, port, timeout, interTransactionDelay,
                            writePriorityMode, reconnectDelay, keepAlive, sortReadQueue
                    );
                    conn.start();
                    connectionPool.put(key, conn);
                }
            } finally {
                lock.unlock();
            }
        }
        return conn;
    }

    public static void removeConnection(String host, int port) {
        String key = host + ":" + port;
        lock.lock();
        try {
            ModbusConnection conn = connectionPool.remove(key);
            if (conn != null) {
                conn.stop();
            }
        } finally {
            lock.unlock();
        }
    }

    public static void registerConnectionReference(String targetId, String name, ModbusConnection conn) {
        if (targetId != null) lookupRegistry.put(targetId, conn);
        if (name != null) lookupRegistry.put(name, conn);
    }

    public static void unregisterConnectionReference(String targetId, String name) {
        if (targetId != null) lookupRegistry.remove(targetId);
        if (name != null) lookupRegistry.remove(name);
    }

    public static ModbusConnection getConnectionByNameOrId(String nameOrId) {
        if (nameOrId == null) return null;
        return lookupRegistry.get(nameOrId);
    }

    // --- INNER CLASSES FOR CONNECTION & TASK QUEUEING ---

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING
    }

    @FunctionalInterface
    public interface ModbusCommand<T> {
        T execute(ModbusMaster master) throws Exception;
    }

    public abstract static class ModbusTask<T> implements Comparable<ModbusTask<?>> {
        public final int priority;
        public final long sequenceNumber;
        public final int unitId;
        public final int address;
        public final int quantity;
        public final CompletableFuture<T> future = new CompletableFuture<>();
        private final boolean sortReadQueue;

        protected ModbusTask(int priority, long seqNum, int unitId, int address, int quantity, boolean sortReadQueue) {
            this.priority = priority;
            this.sequenceNumber = seqNum;
            this.unitId = unitId;
            this.address = address;
            this.quantity = quantity;
            this.sortReadQueue = sortReadQueue;
        }

        public abstract T run(ModbusMaster master) throws Exception;

        public void complete(Object result) {
            @SuppressWarnings("unchecked")
            T casted = (T) result;
            future.complete(casted);
        }

        public void completeExceptionally(Throwable ex) {
            future.completeExceptionally(ex);
        }

        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public int compareTo(ModbusTask<?> other) {
            // 1. Compare priority (lower number = higher priority, e.g. 1 (WRITE) < 2 (READ))
            int pCompare = Integer.compare(this.priority, other.priority);
            if (pCompare != 0) return pCompare;

            // 2. For read tasks, if enabled, sort by unit ID and address
            if (this.priority == 2 && this.sortReadQueue && other.sortReadQueue) {
                int idCompare = Integer.compare(this.unitId, other.unitId);
                if (idCompare != 0) return idCompare;
                return Integer.compare(this.address, other.address);
            }

            // 3. Otherwise, maintain FIFO sequence order
            return Long.compare(this.sequenceNumber, other.sequenceNumber);
        }
    }

    private static class ModbusReadTask<T> extends ModbusTask<T> {
        private final ModbusCommand<T> command;

        public ModbusReadTask(long seqNum, int unitId, int address, int quantity, boolean sortReadQueue, ModbusCommand<T> command) {
            super(2, seqNum, unitId, address, quantity, sortReadQueue);
            this.command = command;
        }

        @Override
        public T run(ModbusMaster master) throws Exception {
            return command.execute(master);
        }
    }

    private static class ModbusWriteTask<T> extends ModbusTask<T> {
        private final ModbusCommand<T> command;

        public ModbusWriteTask(int priority, long seqNum, int unitId, int address, int quantity, ModbusCommand<T> command) {
            super(priority, seqNum, unitId, address, quantity, false);
            this.command = command;
        }

        @Override
        public T run(ModbusMaster master) throws Exception {
            return command.execute(master);
        }
    }

    public static class ModbusConnection {
        private final String host;
        private final int port;
        private final int timeout;
        private final int interTransactionDelayMs;
        private final boolean writePriorityHigh;
        private final int reconnectDelayMs;
        private final boolean keepAlive;
        private final boolean sortReadQueue;

        private final PriorityBlockingQueue<ModbusTask<?>> queue = new PriorityBlockingQueue<>();
        private final ConcurrentHashMap<String, ModbusTask<?>> pendingReads = new ConcurrentHashMap<>();
        private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
        private final AtomicLong seqCounter = new AtomicLong(0);
        private final AtomicBoolean running = new AtomicBoolean(false);

        private ModbusMaster master;
        private Thread workerThread;

        public ModbusConnection(
                String host, int port, int timeout, int interTransactionDelayMs,
                String writePriorityMode, int reconnectDelayMs, boolean keepAlive, boolean sortReadQueue) {
            this.host = host;
            this.port = port;
            this.timeout = timeout;
            this.interTransactionDelayMs = interTransactionDelayMs;
            this.writePriorityHigh = "HIGH".equalsIgnoreCase(writePriorityMode);
            this.reconnectDelayMs = reconnectDelayMs > 0 ? reconnectDelayMs : 5000;
            this.keepAlive = keepAlive;
            this.sortReadQueue = sortReadQueue;
        }

        public ConnectionState getState() {
            return state.get();
        }

        public synchronized void start() throws Exception {
            if (running.compareAndSet(false, true)) {
                TcpParameters tcpParameters = new TcpParameters();
                tcpParameters.setHost(InetAddress.getByName(host));
                tcpParameters.setPort(port);
                tcpParameters.setKeepAlive(keepAlive);
                tcpParameters.setConnectionTimeout(timeout);

                this.master = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
                this.master.setTransactionId(1);

                this.workerThread = Thread.startVirtualThread(this::runWorkerLoop);
                System.out.println("[Modbus Connection] Started executor loop for " + host + ":" + port);
            }
        }

        public synchronized void stop() {
            if (running.compareAndSet(true, false)) {
                if (workerThread != null) {
                    workerThread.interrupt();
                }
                handleDisconnect(new IOException("Modbus connection stopped."));
                System.out.println("[Modbus Connection] Stopped executor loop for " + host + ":" + port);
            }
        }

        private void connectPhysical() throws Exception {
            if (!master.isConnected()) {
                master.connect();
            }
        }

        public <T> CompletableFuture<T> submitRead(
                int unitId, String readType, int address, int quantity, boolean coalesce, ModbusCommand<T> command) {

            if (state.get() == ConnectionState.DISCONNECTED) {
                return CompletableFuture.failedFuture(new IOException("Modbus pool is currently disconnected from " + host + ":" + port));
            }

            if (coalesce) {
                String key = unitId + ":" + readType + ":" + address + ":" + quantity;
                while (true) {
                    ModbusTask<?> existing = pendingReads.get(key);
                    if (existing != null && !existing.future.isDone()) {
                        @SuppressWarnings("unchecked")
                        CompletableFuture<T> reusedFuture = (CompletableFuture<T>) existing.future;
                        return reusedFuture;
                    }

                    long seq = seqCounter.incrementAndGet();
                    ModbusReadTask<T> newTask = new ModbusReadTask<>(seq, unitId, address, quantity, sortReadQueue, command);

                    ModbusTask<?> old = pendingReads.putIfAbsent(key, newTask);
                    if (old == null) {
                        queue.put(newTask);
                        newTask.future.whenComplete((res, ex) -> pendingReads.remove(key, newTask));
                        return newTask.future;
                    }
                    // Loop again if someone else added it in between
                }
            } else {
                long seq = seqCounter.incrementAndGet();
                ModbusReadTask<T> newTask = new ModbusReadTask<>(seq, unitId, address, quantity, sortReadQueue, command);
                queue.put(newTask);
                return newTask.future;
            }
        }

        public <T> CompletableFuture<T> submitWrite(
                int unitId, int address, int quantity, ModbusCommand<T> command) {

            if (state.get() == ConnectionState.DISCONNECTED) {
                return CompletableFuture.failedFuture(new IOException("Modbus pool is currently disconnected from " + host + ":" + port));
            }

            long seq = seqCounter.incrementAndGet();
            int priority = writePriorityHigh ? 1 : 2;
            ModbusWriteTask<T> newTask = new ModbusWriteTask<>(priority, seq, unitId, address, quantity, command);
            queue.put(newTask);
            return newTask.future;
        }

        private void runWorkerLoop() {
            while (running.get()) {
                try {
                    // Reconnection mechanism
                    if (state.get() == ConnectionState.DISCONNECTED) {
                        state.set(ConnectionState.RECONNECTING);
                        try {
                            System.out.println("[Modbus Connection] Connecting to " + host + ":" + port + "...");
                            connectPhysical();
                            state.set(ConnectionState.CONNECTED);
                            System.out.println("[Modbus Connection] Connected to " + host + ":" + port);
                        } catch (Exception e) {
                            state.set(ConnectionState.DISCONNECTED);
                            System.err.println("[Modbus Connection Error] Connection failed to " + host + ":" + port 
                                    + " | " + e.getMessage() + ". Retrying in " + reconnectDelayMs + "ms...");
                            Thread.sleep(reconnectDelayMs);
                            continue;
                        }
                    }

                    // Get next task
                    ModbusTask<?> task = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (task == null) continue;

                    if (task.isCancelled()) continue;

                    try {
                        connectPhysical(); // Ensure connection is up
                        Object result = task.run(master);
                        task.complete(result);
                    } catch (Exception ex) {
                        task.completeExceptionally(ex);
                        if (isNetworkException(ex)) {
                            handleDisconnect(ex);
                        }
                    }

                    if (interTransactionDelayMs > 0) {
                        Thread.sleep(interTransactionDelayMs);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[Modbus Connection Worker Thread Exception] " + e.getMessage());
                }
            }
        }

        private void handleDisconnect(Throwable cause) {
            if (state.get() != ConnectionState.DISCONNECTED) {
                state.set(ConnectionState.DISCONNECTED);
                System.err.println("[Modbus Connection lost] Disconnected from " + host + ":" + port 
                        + " | Reason: " + cause.getMessage());

                try {
                    master.disconnect();
                } catch (Exception ignored) {
                }

                // Immediately cancel all tasks in queue (Fast-fail queue flush)
                List<ModbusTask<?>> drained = new ArrayList<>();
                queue.drainTo(drained);
                for (ModbusTask<?> task : drained) {
                    task.completeExceptionally(new IOException("Modbus connection lost: " + cause.getMessage(), cause));
                }

                pendingReads.clear();
            }
        }

        private boolean isNetworkException(Exception ex) {
            if (ex instanceof ModbusIOException) {
                return true;
            }
            if (ex instanceof IOException) {
                return true;
            }
            Throwable cause = ex.getCause();
            while (cause != null) {
                if (cause instanceof java.net.SocketException || cause instanceof IOException) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }
    }
}
