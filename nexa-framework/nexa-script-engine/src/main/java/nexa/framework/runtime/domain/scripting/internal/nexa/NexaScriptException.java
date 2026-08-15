package nexa.framework.runtime.domain.scripting.internal.nexa;

public final class NexaScriptException extends RuntimeException {

    private final int line;
    private final int column;

    public NexaScriptException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}


