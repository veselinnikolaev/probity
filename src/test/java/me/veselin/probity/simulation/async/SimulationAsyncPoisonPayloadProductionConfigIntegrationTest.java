package me.veselin.probity.simulation.async;

import me.veselin.probity.simulation.domain.Simulation;
import me.veselin.probity.simulation.domain.SimulationStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D0 T4b - a poison payload must dead-letter under the <em>production</em> deserializer
 * config, not just under the slice-wide override.
 *
 * <p>{@link SimulationAsyncUndeserializablePayloadIntegrationTest} proves the DLT routing from
 * the {@code ErrorHandlingDeserializer} override that the async slice injects through
 * {@link AsyncKafkaTestSupport#registerAsyncBroker}. That override would mask a regression in
 * {@code application.yaml}, which is exactly where the PR-D0 fix lives. This class registers
 * with {@code wrapValueDeserializer = false} (see
 * {@link AsyncKafkaTestSupport#registerAsyncBroker(DynamicPropertyRegistry, boolean, String, boolean)}),
 * so the value deserializer is whatever production {@code application.yaml} ships — and the
 * DLT routing has to come from there.
 *
 * <p>Two guarantees are asserted, as in T4a, because a fix that only delivers the dead-letter
 * record still loses the row, and vice versa: the DLT receives a record keyed on the simulation
 * id carrying the original bytes, and the row reaches {@code FAILED} via the recoverer's
 * conditional {@code UPDATE ... WHERE id = :key AND status = 'PENDING'}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationAsyncPoisonPayloadProductionConfigIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    private static final Duration DLT_WAIT = Duration.ofSeconds(20);

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        // wrapValueDeserializer = false: application.yaml must supply the
        // ErrorHandlingDeserializer wrapper itself - that is the PR-D0 fix under test.
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "prd0-poison-prod", false);
    }

    @Test
    @DisplayName("PR-D0: production deserializer config dead-letters an untrusted payload")
    void poisonPayload_isDeadLetteredWithProductionDeserializerConfig() throws Exception {
        UUID id = seedPendingSimulation();
        UntrustedSimulationPayload poison = new UntrustedSimulationPayload(id, "not a SimulationRequestedEvent");

        publish(poison, id);

        List<ConsumerRecord<String, byte[]>> dlt =
                AsyncKafkaTestSupport.drainDlt(DLT_WAIT, 1, "prd0-poison-prod");

        ConsumerRecord<String, byte[]> deadLettered = dlt.stream()
                .filter(r -> id.toString().equals(r.key()))
                .findFirst()
                .orElse(null);
        assertNotNull(deadLettered,
                () -> "no dead-letter record for simulation id " + id + " reached "
                        + AsyncKafkaTestSupport.dltTopicFor("prd0-poison-prod") + " within " + DLT_WAIT
                        + " using the PRODUCTION deserializer config. A bare JsonDeserializer - the "
                        + "pre-PR-D0 yaml - wedges the partition at the poison offset (DefaultErrorHandler "
                        + "refuses a raw SerializationException) instead of dead-lettering. "
                        + describe(id, dlt));

        assertEquals(id.toString(), deadLettered.key(),
                () -> "the DLT record must stay keyed on the simulation id so the row can be "
                        + "resolved from the key alone. " + describe(id, dlt));

        String body = new String(deadLettered.value(), StandardCharsets.UTF_8);
        assertTrue(body.contains("UntrustedSimulationPayload") || body.contains(poison.note()),
                () -> "the DLT must carry the original payload bytes, not a re-serialised "
                        + "placeholder or a bare exception. Saw: " + body);

        Simulation row = simulationRepository.findById(id).orElseThrow();
        assertEquals(SimulationStatus.FAILED, row.getStatus(),
                () -> "the row must reach FAILED via the recoverer's conditional UPDATE on the "
                        + "record key. Its key is readable even though its value is not. "
                        + describe(id, dlt));
    }

    private String describe(UUID id, List<ConsumerRecord<String, byte[]>> dlt) {
        return "dltRecords=" + dlt.size()
                + " keys=" + dlt.stream().map(ConsumerRecord::key).toList()
                + " | " + diagnostics(id, null);
    }
}