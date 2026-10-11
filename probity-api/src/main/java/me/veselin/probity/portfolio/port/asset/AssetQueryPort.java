package me.veselin.probity.portfolio.port.asset;

import me.veselin.probity.portfolio.dto.AssetSearchResultDto;

import java.util.List;

public interface AssetQueryPort {
    List<AssetSearchResultDto> search(String q);
}
