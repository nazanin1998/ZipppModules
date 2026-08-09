package com.zippp.rabbitconsumer.config;

import io.micrometer.common.util.StringUtils;
import org.springframework.amqp.core.AcknowledgeMode;

/**
 * Immutable configuration for a single {@link com.zippp.rabbitconsumer.consumer.MessageConsumer}
 * subscription.
 *
 * <p>Mirrors the parameters Spring's {@code @RabbitListener} annotation accepts,
 * but in code — so the queue name, concurrency, prefetch, ack mode, etc. can be
 * resolved from properties, computed at runtime, or passed in by the target
 * project.
 *
 * <h2>Why a config object and not a builder with 7 setters on MessageConsumer</h2>
 * <ul>
 *   <li>One immutable snapshot — easier to log, copy, compare.</li>
 *   <li>Validation lives in one place ({@link #build()}) rather than at the
 *       first message.</li>
 *   <li>Multiple consumers in one target project can share a single factory
 *       while carrying distinct configs.</li>
 * </ul>
 *
 * <p>Build with {@link #builder(String)}:
 * <pre>{@code
 * ConsumerConfig cfg = ConsumerConfig.builder("orderConsumer")
 *         .queue("orders.queue")
 *         .concurrency(2)
 *         .prefetch(10)
 *         .ackMode(AcknowledgeMode.AUTO)
 *         .build();
 * }</pre>
 */
public final class ConsumerConfig {

    private final String id;
    private final String queue;
    private final int concurrency;
    private final int prefetch;
    private final AcknowledgeMode ackMode;
    private final String exchange;
    private final String routingKey;

    private ConsumerConfig(Builder b) {
        this.id = b.id;
        this.queue = b.queue;
        this.concurrency = b.concurrency;
        this.prefetch = b.prefetch;
        this.ackMode = b.ackMode;
        this.exchange = b.exchange;
        this.routingKey = b.routingKey;
    }

    public String getId() { return id; }
    public String getQueue() { return queue; }
    public int getConcurrency() { return concurrency; }
    public int getPrefetch() { return prefetch; }
    public AcknowledgeMode getAckMode() { return ackMode; }
    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder for {@link ConsumerConfig}. The endpoint {@code id} is required
     * (it identifies the underlying {@code SimpleMessageListenerContainer} in
     * Spring's registry) and must be unique within the application context.
     */
    public static final class Builder {
        private final String id;
        private String queue;
        private int concurrency = 1;
        private int prefetch = 0;       // 0 = defer to broker default
        private AcknowledgeMode ackMode = AcknowledgeMode.AUTO;
        private String exchange = null;
        private String routingKey = null;

        private Builder(String id) {
            if (StringUtils.isBlank(id)) {
                throw new IllegalArgumentException("id is blank");
            }
            this.id = id;
        }

        public Builder queue(String queue) { this.queue = queue; return this; }
        public Builder concurrency(int concurrency) { this.concurrency = concurrency; return this; }
        public Builder prefetch(int prefetch) { this.prefetch = prefetch; return this; }
        public Builder ackMode(AcknowledgeMode ackMode) { this.ackMode = ackMode; return this; }
        public Builder exchange(String exchange) { this.exchange = exchange; return this; }
        public Builder routingKey(String routingKey) { this.routingKey = routingKey; return this; }

        /**
         * Validates required fields and returns the immutable config. Throws
         * {@link IllegalArgumentException} on missing queue or invalid ranges.
         */
        public ConsumerConfig build() {
            if (StringUtils.isBlank(queue)) {
                throw new IllegalArgumentException("queue is blank");
            }
            if (concurrency < 1) {
                throw new IllegalArgumentException("concurrency must be >= 1");
            }
            if (prefetch < 0) {
                throw new IllegalArgumentException("prefetch must be >= 0");
            }
            if (ackMode == null) {
                throw new IllegalArgumentException("ackMode is null");
            }
            return new ConsumerConfig(this);
        }
    }
}
