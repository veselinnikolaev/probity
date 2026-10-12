package me.veselin.probity.simulation.async;

import java.util.UUID;

/**
 * A payload the consumer is not allowed to deserialise.
 *
 * <p>{@code application.yaml} sets {@code spring.json.trusted.packages} to
 * {@code me.veselin.probity.common.domain.event} only. This record lives in
 * {@code me.veselin.probity.simulation.async}, so the {@code __TypeId__} header the
 * producer writes is untrusted and {@code JsonDeserializer} refuses it.
 *
 * <p>The simulation id is carried in the record key rather than in the payload, because
 * a record whose value cannot be deserialised still has a readable key - which is what
 * lets the recoverer mark the row terminal.
 */
public record UntrustedSimulationPayload(UUID simulationId, String note) {
}
