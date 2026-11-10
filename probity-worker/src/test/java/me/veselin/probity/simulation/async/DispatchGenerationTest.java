package me.veselin.probity.simulation.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.veselin.probity.common.domain.event.DispatchGeneration;
import org.junit.jupiter.api.Test;

class DispatchGenerationTest {

  @Test
  void rejectsMalformedHeaderWithoutApplyingLegacyDefault() {
    DispatchGeneration.Resolution result =
        DispatchGeneration.resolve("0003", null, true);

    assertEquals(DispatchGeneration.Source.MALFORMED, result.source());
    assertNull(result.value());
  }

  @Test
  void rejectsConflictingCarriers() {
    DispatchGeneration.Resolution result =
        DispatchGeneration.resolve("2", 3, true);

    assertEquals(DispatchGeneration.Source.CORRUPT, result.source());
    assertNull(result.value());
  }

  @Test
  void usesLegacyDefaultOnlyWhenBothCarriersAreAbsent() {
    DispatchGeneration.Resolution result =
        DispatchGeneration.resolve(null, null, true);

    assertEquals(DispatchGeneration.Source.LEGACY_DEFAULT, result.source());
    assertEquals(1, result.value());
  }
}
