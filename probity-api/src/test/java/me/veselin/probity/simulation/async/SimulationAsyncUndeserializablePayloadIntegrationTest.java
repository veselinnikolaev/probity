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
 * T4a - an undeserialisable payload must end up in the dead-letter topic with its bytes
 * intact, and its row must be marked {@code FAILED} from the record key.
 *
 * <p>Mechanism under test, as measured on {@code master}: {@code application.yaml}
 * configures a bare {@code JsonDeserializer} with no {@code ErrorHandlingDeserializer}
 * wrapper, so a value that fails to deserialise is handed to the error handler as a
 * {@code RecordDeserializationException} carrying no deserialised value.
 * {@code DefaultErrorHandler.handleOtherException} rejects that outright with
 * {@code IllegalStateException("This error handler cannot process 'SerializationException's
 * directly")}, and the container logs it and polls again without ever seeking past the
 * record. Measured: 21,177 identical exceptions across 22 seconds, all at offset 0, with
 * the group offset never committed and nothing ever published to the DLT.
 *
 * <p>So master neither drops the record nor dead-letters it - it <strong>wedges the
 * partition</strong>. Because offset 0 is never committed and never seeked past, every
 * later record on that partition is unreachable too, including well-formed events for
 * simulations that are pending and harmless. One bad byte from any producer stops the
 * entire async pipeline. The row it named also sits at {@code PENDING} forever.
 *
 * <p>Two independent guarantees are asserted, because a fix that only delivers the
 * dead-letter record still loses the row, and vice versa:
 * <ol>
 *   <li>the DLT receives a record keyed on the simulation id, carrying the original bytes;</li>
 *   <li>the row reaches {@code FAILED}, resolved from {@code ConsumerRecord.key()} - a
 *       conditional {@code UPDATE ... WHERE id = :key AND status = 'PENDING'}.</li>
 * </ol>
 *
 * <p>Context is dirtied so the deliberately failing record cannot outlive this class.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationAsyncUndeserializablePayloadIntegrationTest extends BaseAsyncSimulationIntegrationTest {

    private static final Duration DLT_WAIT = Duration.ofSeconds(20);

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        AsyncKafkaTestSupport.registerAsyncBroker(registry, true, "t4a-undeserializable");
    }

    @Test
    @DisplayName("T4a: an untrusted payload is dead-lettered with its bytes and fails the row")
    void undeserializablePayload_isDeadLetteredAndFailsTheRow() throws Exception {
        UUID id = seedPendingSimulation();
        UntrustedSimulationPayload poison = new UntrustedSimulationPayload(id, "not a SimulationRequestedEvent");

        // Keyed on the simulation id, exactly as SimulationEventPublisher keys real events.
        publish(poison, id);

        // Drain this class's own DLT and pick out the record for this simulation by its
        // key. "First record" is not a safe anchor: the drain may legitimately see records
        // from earlier retries or sibling tests, and the DLT is per-class precisely so the
        // key filter below has exactly one match to find.
        List<ConsumerRecord<String, byte[]>> dlt =
                AsyncKafkaTestSupport.drainDlt(DLT_WAIT, 1, "t4a-undeserializable");

        ConsumerRecord<String, byte[]> deadLettered = dlt.stream()
                .filter(r -> id.toString().equals(r.key()))
                .findFirst()
                .orElse(null);
        assertNotNull(deadLettered,
                () -> "no dead-letter record for simulation id " + id + " reached "
                        + AsyncKafkaTestSupport.dltTopicFor("t4a-undeserializable") + " within " + DLT_WAIT
                        + ". An undeserialisable payload must be recoverable from the DLT. On "
                        + "master nothing is published at all: DefaultErrorHandler refuses a bare "
                        + "SerializationException, the container never seeks past the record, and "
                        + "the partition is left blocked at that offset. " + describe(id, dlt));

        assertEquals(id.toString(), deadLettered.key(),
                () -> "the DLT record must stay keyed on the simulation id so the row can be "
                        + "resolved from the key alone. " + describe(id, dlt));

        String deadLetteredBody = new String(deadLettered.value(), StandardCharsets.UTF_8);
        assertTrue(deadLetteredBody.contains("UntrustedSimulationPayload") || deadLetteredBody.contains(poison.note()),
                () -> "the DLT must carry the original payload bytes, not a re-serialised "
                        + "placeholder or a bare exception. Saw: " + deadLetteredBody);

        Simulation row = simulationRepository.findById(id).orElseThrow();
        assertEquals(SimulationStatus.FAILED, row.getStatus(),
                () -> "the row must reach FAILED. Its key is readable even though its value is "
                        + "not, so the recoverer can resolve it with a conditional UPDATE on "
                        + "status = 'PENDING'. " + describe(id, dlt));
    }

    private String describe(UUID id, List<ConsumerRecord<String, byte[]>> dlt) {
        return "dltRecords=" + dlt.size()
                + " keys=" + dlt.stream().map(ConsumerRecord::key).toList()
                + " | " + diagnostics(id, null);
    }
}
