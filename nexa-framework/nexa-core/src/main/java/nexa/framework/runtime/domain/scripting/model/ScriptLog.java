package nexa.framework.runtime.domain.scripting.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ScriptLog {

    private static final DateTimeFormatter CONSOLE_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final String nodeId;

    public ScriptLog(String nodeId) {
        this.nodeId = nodeId;
    }

    public void debug(Object... args) {
        write("DEBUG", args, false);
    }

    public void info(Object... args) {
        write("INFO", args, false);
    }

    public void warn(Object... args) {
        write("WARN", args, false);
    }

    public void error(Object... args) {
        write("ERROR", args, true);
    }

    private void write(String level, Object[] args, boolean errorStream) {
        String timestamp = CONSOLE_TIMESTAMP_FORMATTER.format(Instant.now());
        StringBuilder message = new StringBuilder();
        for (int index = 0; index < args.length; index++) {
            if (index > 0) {
                message.append(' ');
            }
            message.append(String.valueOf(args[index]));
        }

        String line = "[log][" + level + "][" + timestamp + "][" + nodeId + "] " + message;
        if (errorStream) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
    }
}


