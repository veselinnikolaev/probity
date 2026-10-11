package me.veselin.probity.simulation.async;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Shared Testcontainers Kafka broker for the async simulation slice.
 *
 * <p>One broker is started per test JVM and reused by every async test class
 * (the pattern already used by {@link me.veselin.probity.BaseIntegrationTest}
 * for Postgres and Redis).
 *
 * <p>Image: {@code apache/kafka:3.7.0} - the official KRaft image, and the image
 * {@code org.testcontainers.kafka.KafkaContainer} is built for. It is present in
 * the local Docker image cache, so the offline build never pulls.
 *
 * <p><strong>Note on consumer groups.</strong> {@code SimulationRequestedEventConsumer}
 * resolves its {@code groupId} from {@code probity.simulation.consumer-group}, and
 * {@link #registerAsyncBroker} points that at a group derived from the suffix each test
 * class passes. This is not cosmetic. The topic has a single partition, and every Spring
 * context in this slice stays cached — and therefore keeps its listener container running —
 * until the JVM exits. With one shared group, the first container to start takes the only
 * partition and every later class's record is executed by a <em>different</em> class's
 * consumer: its assertions then observe someone else's executions, and {@code describeConsumer}
 * reports whichever container happens to come first in the registry. Per-class groups make
 * each container the only member of its own group, so it is guaranteed the partition and the
 * only thing that will execute that class's records.
 *
 * <p>Each class also registers its own source and dead-letter topics
 * ({@link #sourceTopicFor(String)} / {@link #dltTopicFor(String)}) alongside its group. Were
 * the topic shared, a fresh group with {@code auto-offset-reset=earliest} would replay every
 * record this JVM ever published — including other classes' — and execute them against the
 * wrong rows while the owning class's test is still running. Per-class topics make that
 * impossible: a container only ever sees records its own class published.
 */
public final class AsyncKafkaTestSupport {

    /** Source topic, matches the default for {@code probity.kafka.simulation-requested-topic}. */
    public static final String SOURCE_TOPIC = "simulation-requested";

    /** Dead-letter topic, matches the default for {@code probity.kafka.simulation-requested-dlt-topic}. */
    public static final String DLT_TOPIC = "simulation-requested.DLT";

    /** The source topic a given test class's listener consumes from. */
    public static String sourceTopicFor(String suffix) {
        return SOURCE_TOPIC + "-" + suffix;
    }

    /** The dead-letter topic a given test class's failed records land on. */
    public static String dltTopicFor(String suffix) {
        return DLT_TOPIC + "-" + suffix;
    }

    /**
     * Prefix for the per-class consumer groups.
     *
     * <p>Each live-listener test class gets {@code probity-simulation-worker-<suffix>}. The
     * suffix must be unique across the slice — see the note on consumer groups on this class.
     */
    public static final String CONSUMER_GROUP_PREFIX = "probity-simulation-worker-";

    /** The production default, used when nothing overrides the property. */
    public static final String DEFAULT_CONSUMER_GROUP = "probity-simulation-worker";

    /** The group a given test class's consumer listens as. */
    public static String consumerGroup(String suffix) {
        return CONSUMER_GROUP_PREFIX + suffix;
    }

    /**
     * A broker address nothing can be listening on.
     *
     * <p>Port 1 is privileged: binding it needs root, so on every platform this suite
     * supports the connection is refused immediately rather than hanging. Used by
     * {@link #registerDormantListener} so a dormant-listener context cannot reach any
     * cluster at all, instead of depending on the absence of a broker on 9092.
     */
    public static final String UNREACHABLE_BROKER = "localhost:1";

    public static final KafkaContainer KAFKA = startBroker();

    private AsyncKafkaTestSupport() {
    }

    private static KafkaContainer startBroker() {
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
                .withStartupTimeout(Duration.ofMinutes(2));
        kafka.start();
        return kafka;
    }

    /**
     * Points a test class at the shared broker, in a consumer group of its own and on
     * source/DLT topics of its own.
     *
     * <p>Each class registers {@code probity.kafka.simulation-requested-topic} /
     * {@code probity.kafka.simulation-requested-dlt-topic} as {@link #sourceTopicFor(String)}
     * / {@link #dltTopicFor(String)}. Together with the per-class group this makes cross-class
     * event replay impossible: no other container shares this class's topic <em>or</em> its
     * group, so every record this class's DLT probe reads is this class's own failure.
     *
     * <p>The value deserializer is wrapped in an {@code ErrorHandlingDeserializer} (delegating
     * to the same {@code JsonDeserializer} production uses), so an undeserialisable payload is
     * handed to the error handler as a normal listener exception — and then to the recoverer,
     * which restores the original bytes — instead of wedging the partition (T4a).
     *
     * @param autoStartup whether the simulation-requested listener should actually start.
     *                    {@code false} keeps the consumer dormant.
     * @param groupSuffix unique across this slice; see {@link #consumerGroup(String)}
     */
    public static void registerAsyncBroker(DynamicPropertyRegistry registry,
                                          boolean autoStartup,
                                          String groupSuffix) {
        registerAsyncBroker(registry, autoStartup, groupSuffix, true);
    }

    /**
     * As {@link #registerAsyncBroker(DynamicPropertyRegistry, boolean, String)}, with a
     * choice on the value deserializer.
     *
     * <p>Passing {@code wrapValueDeserializer = false} keeps the production
     * {@code spring.kafka.consumer.value-deserializer} from {@code application.yaml}
     * untouched, so a test can prove the production config itself wraps the value
     * deserializer in an {@code ErrorHandlingDeserializer} (the PR-D0 fix) instead of
     * relying on the override this support class normally injects.
     *
     * @param wrapValueDeserializer {@code false} to keep the production deserializer config.
     */
    public static void registerAsyncBroker(DynamicPropertyRegistry registry,
                                          boolean autoStartup,
                                          String groupSuffix,
                                          boolean wrapValueDeserializer) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> autoStartup);
        registry.add("probity.simulation.consumer-group", () -> consumerGroup(groupSuffix));
        registry.add("probity.kafka.simulation-requested-topic", () -> sourceTopicFor(groupSuffix));
        registry.add("probity.kafka.simulation-requested-dlt-topic", () -> dltTopicFor(groupSuffix));
        // The broker is brand new every JVM, so the group has no committed offsets and
        // 'earliest' means "everything this JVM published" - which removes the
        // assignment-vs-position-resolution race that 'latest' would leave open.
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        if (wrapValueDeserializer) {
            // ErrorHandlingDeserializer around the production JsonDeserializer: a value that fails
            // to deserialise becomes a recoverable listener exception instead of a Fetcher-level
            // SerializationException that DefaultErrorHandler refuses (see the T4a javadoc).
            registry.add("spring.kafka.consumer.value-deserializer",
                    () -> "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
            registry.add("spring.kafka.consumer.properties.spring.deserializer.value.delegate.class",
                    () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
        }
    }

    /**
     * Keeps the listener dormant and the broker unreachable.
     *
     * <p>Needed by tests that exercise the request half of the async flow and then assert
     * on the untouched row — T1 above all, plus the repository-only T3b probe.
     *
     * <p>The requirement is: <em>no broker this context can reach, and no running
     * listener</em>. Both are established absolutely rather than by accident:
     *
     * <ul>
     *   <li>{@code spring.kafka.bootstrap-servers} is repointed at {@link #UNREACHABLE_BROKER}.
     *       The previous version left it at {@code application.yaml}'s
     *       {@code localhost:9092}, so the test silently depended on whatever the developer
     *       happened to be running — and, worse, on the <em>absence</em> of a consumer for
     *       it. The listener pins its {@code groupId} in the annotation, so every class in
     *       this slice shares one group; a container left running by a cached Spring context
     *       could claim a row mid-assertion if the two clusters were the same. A privileged
     *       port nothing binds removes the possibility instead of relying on it.</li>
     *   <li>Callers additionally mock {@code SimulationEventPublisher} with
     *       {@code @MockitoBean}, so nothing is sent and the request path never touches the
     *       network — which also removes the 60 s {@code max.block.ms} and metadata-timeout
     *       noise a dead broker produces.</li>
     * </ul>
     */
    public static void registerDormantListener(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> UNREACHABLE_BROKER);
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    /**
     * Drains a dead-letter topic with a throwaway consumer and returns the raw
     * records, so callers can assert on exact payload bytes.
     *
     * <p>Polls until {@code minRecords} have been seen or {@code timeout} elapses.
     * A short result when nothing was published is a legitimate outcome the caller must
     * assert on, not an error here.
     *
     * <p>Without a {@code suffix}, drains the production default topic; with one, drains
     * {@link #dltTopicFor(String)} so a class reads only its own failures.
     */
    public static List<ConsumerRecord<String, byte[]>> drainDlt(Duration timeout, int minRecords) {
        return drainDlt(timeout, minRecords, null);
    }

    /**
     * As {@link #drainDlt(Duration, int)}, but drains that class's per-class DLT
     * ({@link #dltTopicFor(String)}).
     */
    public static List<ConsumerRecord<String, byte[]>> drainDlt(Duration timeout, int minRecords, String suffix) {
        String topic = suffix == null ? DLT_TOPIC : dltTopicFor(suffix);
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "probity-dlt-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        List<ConsumerRecord<String, byte[]>> collected = new ArrayList<>();
        try (Consumer<String, byte[]> probe = new KafkaConsumer<>(props)) {
            probe.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline && collected.size() < minRecords) {
                ConsumerRecords<String, byte[]> polled = probe.poll(Duration.ofMillis(500));
                polled.forEach(collected::add);
            }
        }
        return collected;
    }
}
