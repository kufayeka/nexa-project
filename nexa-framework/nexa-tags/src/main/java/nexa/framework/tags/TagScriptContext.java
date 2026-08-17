package nexa.framework.tags;

/** Runtime properties exposed to a tag calculation script as self.*. */
public record TagScriptContext(
        Object oldValue,
        Object newValue,
        Object value,
        String path,
        long timestamp,
        TagQuality quality
) {
    public static TagScriptContext from(TagValue event) {
        return new TagScriptContext(event.oldValue(), event.newValue(), event.value(), event.path(), event.timestamp(), event.quality());
    }
}
