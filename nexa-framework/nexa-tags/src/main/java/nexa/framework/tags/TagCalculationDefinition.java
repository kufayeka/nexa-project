package nexa.framework.tags;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TagCalculationDefinition(
        TriggerType triggerType,
        String intervalExpr,
        String script
) {
    public enum TriggerType { ON_CHANGE, ON_WRITE, INTERVAL }

    @JsonCreator
    public TagCalculationDefinition(
            @JsonProperty("triggerType") TriggerType triggerType,
            @JsonProperty("intervalExpr") String intervalExpr,
            @JsonProperty("script") String script) {
        this.triggerType = triggerType;
        this.intervalExpr = intervalExpr;
        this.script = script;
    }
}
