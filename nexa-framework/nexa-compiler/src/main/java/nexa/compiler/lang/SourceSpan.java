package nexa.compiler.lang;

public record SourceSpan(int start, int end) {
    public SourceSpan {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid source span");
    }
}
