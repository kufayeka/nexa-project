package nexa.plugin.asset.dto;

import java.util.List;
import java.util.Map;

public record AssetDto(
    String name,
    String template,
    Map<String, Object> parameters,
    List<AttributeDto> attributes,
    List<AssetDto> children
) {}
