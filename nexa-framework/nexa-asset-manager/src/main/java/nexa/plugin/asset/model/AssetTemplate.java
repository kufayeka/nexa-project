package nexa.plugin.asset.model;

import java.util.List;

public record AssetTemplate(
    String name,
    List<TemplateAttribute> attributes
) {
    public record TemplateAttribute(
        String name,
        String dataType,
        Object value,
        Attribute.CalculationConfig calculationConfig
    ) {}
}
