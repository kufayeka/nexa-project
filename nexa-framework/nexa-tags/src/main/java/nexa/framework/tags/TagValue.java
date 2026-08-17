package nexa.framework.tags;

public record TagValue(
        String path,
        Object value,
        Object oldValue,
        Object newValue,
        long timestamp,
        TagQuality quality
) {
    public TagValue {
        if (quality == null) quality = TagQuality.GOOD;
    }
}
