package me.veselin.probity.portfolio.port.asset;

import java.util.List;
import me.veselin.probity.portfolio.dto.AssetSearchResultDto;

public interface AssetQueryPort {
  List<AssetSearchResultDto> search(String q);
}
