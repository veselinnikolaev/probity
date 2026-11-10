package me.veselin.probity.simulation.dlt;

public enum WriteResult {
  RECORDED,
  RECORDED_STALE,
  RECORDED_FUTURE,
  RECORDED_MALFORMED,
  NOT_OWNER
}
