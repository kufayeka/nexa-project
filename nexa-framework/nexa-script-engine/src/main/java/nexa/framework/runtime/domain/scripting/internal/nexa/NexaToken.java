package nexa.framework.runtime.domain.scripting.internal.nexa;

public record NexaToken(
        NexaTokenType type,
        String text,
        int line,
        int column) {
}


