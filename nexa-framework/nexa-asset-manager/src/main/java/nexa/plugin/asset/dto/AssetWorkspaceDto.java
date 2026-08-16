package nexa.plugin.asset.dto;

import nexa.plugin.asset.model.AssetTemplate;
import java.util.List;

public record AssetWorkspaceDto(
    String id,
    List<AssetTemplate> templates,
    List<AssetDto> assets
) {}
