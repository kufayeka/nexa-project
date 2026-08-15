package nexa.framework.runtime.domain.scripting.exception;

public final class StopScriptExecutionException extends RuntimeException {

    public StopScriptExecutionException() {
        super("Node execution stopped by script");
    }
}


