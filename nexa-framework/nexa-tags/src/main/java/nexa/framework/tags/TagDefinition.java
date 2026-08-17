package nexa.framework.tags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TagDefinition(
        String name,
        TagDataType dataType,
        Object value,
        boolean highSpeed
) {
    @JsonCreator
    public TagDefinition(
            @JsonProperty("name") String name,
            @JsonProperty("dataType") TagDataType dataType,
            @JsonProperty("value") Object value,
            @JsonProperty("highSpeed") Boolean highSpeed) {
        this.name = name;
        this.dataType = dataType == null ? TagDataType.OBJECT : dataType;
        this.value = value;
        this.highSpeed = highSpeed != null && highSpeed;
    }

    public TagDefinition(String name, TagDataType dataType, Object value) {
        this(name, dataType, value, false);
    }
}
