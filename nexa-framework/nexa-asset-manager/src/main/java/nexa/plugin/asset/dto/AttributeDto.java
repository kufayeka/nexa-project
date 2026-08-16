package nexa.plugin.asset.dto;

import nexa.plugin.asset.model.Attribute;

public record AttributeDto(
    String name,
    String dataType,
    Object value,
    Attribute.CalculationConfig calculationConfig
) {}
