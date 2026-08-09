//package com.zippp.rabbitconsumer.consumer;
//
//import com.zippp.rabbitconsumer.config.ConsumerConfig;
//import com.zippp.rabbitconsumer.exception.RabbitConsumerException;
//import com.zippp.rabbitconsumer.exception.RabbitConsumerNullArgumentException;
//import com.zippp.rabbitconsumer.handler.MessageHandler;
//import com.zippp.rabbitconsumer.handler.RequestReplyHandler;
//import io.micrometer.common.util.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.slf4j.MDC;
//import org.springframework.amqp.core.AcknowledgeMode;
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.core.MessageProperties;
//import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
//import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
//import org.springframework.amqp.support.converter.MessageConverter;
//import org.springframework.amqp.support.converter.SmartMessageConverter;
//import org.springframework.context.SmartLifecycle;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * A single subscription to a RabbitMQ queue, owned by the target project.
// *
// * <p>This class is deliberately <strong>not a Spring bean</strong>. The target
// * project constructs one instance per logical subscription inside a
// * {@code @Bean} method, passing in the queue configuration and the handler
// * callback. Spring then drives its lifecycle through the {@link SmartLifecycle}
// * interface — no manual start/stop calls needed.
// *
// * <h2>Why a class, not a {@code @RabbitListener} annotation</h2>
// * <p>Under the hood this still creates the same
// * {@link SimpleMessageListenerContainer} that {@code @RabbitListener} would.
// * The benefit of the explicit class is that <em>configuration is data</em>:
// * queue name, concurrency, prefetch, ack mode can come from properties, be
// * computed at runtime, or vary per tenant. None of that requires an
// * annotation.
// *
// * <h2>Two flavors</h2>
// * <ul>
// *   <li>{@link #receive(ConsumerConfig, SimpleRabbitListenerContainerFactory, MessageConverter, MessageHandler)}
// *       — fire-and-forget consume; the handler returns nothing.</li>
// *   <li>{@link #receiveAndReply(ConsumerConfig, SimpleRabbitListenerContainerFactory, MessageConverter, ConnectionFactory, RequestReplyHandler)}
// *       — request/reply consume; the handler's return value is sent back to
// *       the producer via the message's {@code reply-to} header.</li>
// * </ul>
// *
// * <h2>Usage</h2>
// * <pre>{@code
// * @Configuration
// * class MyConsumers {
// *
// *     @Bean
// *     MessageConsumer orderConsumer(SimpleRabbitListenerContainerFactory factory) {
// *         return MessageConsumer.receive(
// *             ConsumerConfig.builder("orderConsumer")
// *                 .queue("orders.queue")
// *                 .concurrency(2)
// *                 .prefetch(10)
// *                 .build(),
// *             factory,
// *             (payload, headers) -> handleOrder(payload)
// *         );
// *     }
// *
// *     @Bean
// *     MessageConsumer orderRpc(SimpleRabbitListenerContainerFactory factory,
// *                              ConnectionFactory cf) {
// *         return MessageConsumer.receiveAndReply(
// *             ConsumerConfig.builder("orderRpc")
// *                 .queue("orders.rpc")
// *                 .build(),
// *             factory,
// *             cf,
// *             (request, headers) -> computeReply(request)
// *         );
// *     }
// * }
// * }</pre>
// *
// * <p>Spring starts the underlying container automatically once the application
// * context is refreshed; it stops the container cleanly on shutdown.
// */
//public class MessageConsumer implements SmartLifecycle {
//
//    /** MDC key matching the producer's correlation convention. */
//    private static final String MDC_MESSAGE_ID = "messageId";
//
//    /** AMQP header carrying the producer-generated correlation id. */
//    private static final String HEADER_MESSAGE_ID = "x-message-id";
//
//    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);
//
//    private final ConsumerConfig config;
//    private final ContainerBuilder builder;
//    private volatile SimpleMessageListenerContainer container;
//    private volatile boolean running = false;
//
//    private MessageConsumer(ConsumerConfig config, ContainerBuilder builder) {
//        validateConfig(config);
//        this.config = config;
//        this.builder = builder;
//    }
//
//    // ------------------------------------------------------------------
//    // factories
//    // ------------------------------------------------------------------
//
//    /**
//     * Build a fire-and-forget consumer.
//     *
//     * @param config      queue + container settings
//     * @param factory     Spring's listener container factory (carries the
//     *                    connection factory)
//     * @param converter   JSON converter used to deserialize incoming payloads;
//     *                    must implement {@link SmartMessageConverter}
//     *                    (e.g. {@code JacksonJsonMessageConverter})
//     * @param handler     business callback invoked once per message
//     */
//    public static MessageConsumer receive(ConsumerConfig config,
//                                          SimpleRabbitListenerContainerFactory factory,
//                                          MessageConverter converter,
//                                          MessageHandler<Object> handler) {
//        validateFactory(factory);
//        SmartMessageConverter smart = requireSmartConverter(converter);
//        if (handler == null) {
//            throw new RabbitConsumerNullArgumentException("handler is null");
//        }
//        return new MessageConsumer(config, () -> buildReceiveContainer(config, factory, smart, handler));
//    }
//
//    /**
//     * Build a request/reply consumer. The connection factory is needed so the
//     * consumer can publish the reply back to the producer's {@code reply-to}
//     * queue.
//     *
//     * @param config      queue + container settings
//     * @param factory     Spring's listener container factory
//     * @param converter   JSON converter used to deserialize the request and
//     *                    serialize the reply
//     * @param connectionFactory  used to publish replies
//     * @param handler     business callback that computes the reply
//     */
//    public static MessageConsumer receiveAndReply(ConsumerConfig config,
//                                                  SimpleRabbitListenerContainerFactory factory,
//                                                  MessageConverter converter,
//                                                  ConnectionFactory connectionFactory,
//                                                  RequestReplyHandler<Object, Object> handler) {
//        validateFactory(factory);
//        SmartMessageConverter smart = requireSmartConverter(converter);
//        if (connectionFactory == null) {
//            throw new RabbitConsumerNullArgumentException("connectionFactory is null");
//        }
//        if (handler == null) {
//            throw new RabbitConsumerNullArgumentException("handler is null");
//        }
//        RabbitTemplate replyTemplate = new RabbitTemplate(connectionFactory);
//        replyTemplate.setMessageConverter(converter);
//
//        return new MessageConsumer(config,
//                () -> buildReplyContainer(config, factory, smart, replyTemplate, handler));
//    }
//
//    private static SmartMessageConverter requireSmartConverter(MessageConverter converter) {
//        if (converter == null) {
//            throw new RabbitConsumerNullArgumentException("converter is null");
//        }
//        if (!(converter instanceof SmartMessageConverter smart)) {
//            throw new RabbitConsumerException(
//                    "MessageConverter must implement SmartMessageConverter " +
//                    "(e.g. JacksonJsonMessageConverter), got " + converter.getClass().getName());
//        }
//        return smart;
//    }
//
//    // ------------------------------------------------------------------
//    // SmartLifecycle
//    // ------------------------------------------------------------------
//
//    @Override
//    public void start() {
//        if (running) {
//            return;
//        }
//        SimpleMessageListenerContainer built = builder.build();
//        built.start();
//        this.container = built;
//        this.running = true;
//        log.info("(RABBIT-CONSUMER) - started: id={}, queue={}, concurrency={}, prefetch={}, ackMode={}",
//                config.getId(), config.getQueue(), config.getConcurrency(),
//                config.getPrefetch(), config.getAckMode());
//    }
//
//    @Override
//    public void stop() {
//        stop(() -> {});
//    }
//
//    @Override
//    public void stop(Runnable callback) {
//        if (!running) {
//            callback.run();
//            return;
//        }
//        SimpleMessageListenerContainer c = this.container;
//        try {
//            if (c != null) {
//                c.stop();
//            }
//        } catch (RuntimeException e) {
//            log.warn("(RABBIT-CONSUMER) - error stopping container id={}: {}",
//                    config.getId(), e.getMessage());
//        } finally {
//            this.running = false;
//            log.info("(RABBIT-CONSUMER) - stopped: id={}", config.getId());
//            callback.run();
//        }
//    }
//
//    @Override
//    public boolean isRunning() {
//        return running;
//    }
//
//    @Override
//    public int getPhase() {
//        // Higher phase = start later, stop earlier. Listeners are network-touching
//        // startup; let application-level beans come up first.
//        return Integer.MAX_VALUE - 100;
//    }
//
//    @Override
//    public boolean isAutoStartup() {
//        return true;
//    }
//
//    // ------------------------------------------------------------------
//    // internals
//    // ------------------------------------------------------------------
//
//    /** Strategy that builds the underlying container. */
//    @FunctionalInterface
//    private interface ContainerBuilder {
//        SimpleMessageListenerContainer build();
//    }
//
//    private static SimpleMessageListenerContainer buildReceiveContainer(
//            ConsumerConfig config,
//            SimpleRabbitListenerContainerFactory factory,
//            MessageHandler<Object> handler) {
//
//        SimpleMessageListenerContainer container = factory.createListenerContainer();
//        container.setQueueNames(config.getQueue());
//        container.setAcknowledgeMode(config.getAckMode());
//        applyConcurrencyAndPrefetch(container, config);
//
//        MessageConverter converter = factory.getMessageConverter();
//        ChannelAwareMessageListener listener = (Message message, com.rabbitmq.client.Channel channel) -> {
//            String messageId = headerString(message, HEADER_MESSAGE_ID, UUID.randomUUID().toString());
//            MDC.put(MDC_MESSAGE_ID, messageId);
//            try {
//                Object payload = converter.fromMessage(message);
//                Map<String, Object> headers = immutableHeaders(message);
//                log.info("(RABBIT-CONSUMER) - received: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId);
//                handler.onMessage(payload, headers);
//                log.debug("(RABBIT-CONSUMER) - handler completed: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId);
//            } catch (RuntimeException e) {
//                log.error("(RABBIT-CONSUMER) - handler exception: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId, e);
//                throw new RabbitConsumerException("receive handler exception", e);
//            } catch (Exception e) {
//                log.error("(RABBIT-CONSUMER) - handler exception: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId, e);
//                throw new RabbitConsumerException("receive handler exception", e);
//            } finally {
//                MDC.remove(MDC_MESSAGE_ID);
//            }
//        };
//        container.setMessageListener(listener);
//        return container;
//    }
//
//    private static SimpleMessageListenerContainer buildReplyContainer(
//            ConsumerConfig config,
//            SimpleRabbitListenerContainerFactory factory,
//            SmartMessageConverter converter,
//            RabbitTemplate replyTemplate,
//            RequestReplyHandler<Object, Object> handler) {
//
//        SimpleMessageListenerContainer container = factory.createListenerContainer();
//        container.setQueueNames(config.getQueue());
//        // MANUAL ack: we MUST ack only after the reply has been published;
//        // otherwise the request message would be redelivered even on a
//        // successful reply.
//        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
//        applyConcurrencyAndPrefetch(container, config);
//
//        ChannelAwareMessageListener listener = (Message message, com.rabbitmq.client.Channel channel) -> {
//            String messageId = headerString(message, HEADER_MESSAGE_ID, UUID.randomUUID().toString());
//            MDC.put(MDC_MESSAGE_ID, messageId);
//            try {
//                Object request = converter.fromMessage(message, Object.class);
//                Map<String, Object> headers = immutableHeaders(message);
//
//                MessageProperties messageProperties = message.getMessageProperties();
//                String replyTo = messageProperties.getReplyTo();
//                String correlationId = messageProperties.getCorrelationId();
//                long deliveryTag = messageProperties.getDeliveryTag();
//
//                log.info("(RABBIT-CONSUMER) - received request: id={}, queue={}, messageId={}, replyTo={}",
//                        config.getId(), config.getQueue(), messageId, replyTo);
//
//                Object reply = handler.onRequest(request, headers);
//
//                if (reply != null && replyTo != null) {
//                    Message replyMessage = converter.toMessage(reply, new MessageProperties());
//                    if (correlationId != null) {
//                        replyMessage.getMessageProperties().setCorrelationId(correlationId);
//                    }
//                    replyTemplate.send(replyTo, replyMessage);
//                    log.info("(RABBIT-CONSUMER) - reply sent: id={}, queue={}, replyType={}, messageId={}",
//                            config.getId(), config.getQueue(), reply.getClass().getName(), messageId);
//                } else {
//                    log.debug("(RABBIT-CONSUMER) - no reply sent: id={}, queue={}, messageId={}, replyNull={}, replyTo={}",
//                            config.getId(), config.getQueue(), messageId, reply == null, replyTo);
//                }
//
//                if (channel.isOpen()) {
//                    channel.basicAck(deliveryTag, false);
//                }
//            } catch (RuntimeException e) {
//                log.error("(RABBIT-CONSUMER) - request handler exception: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId, e);
//                safeNack(channel, message);
//                throw new RabbitConsumerException("receiveAndReply handler exception", e);
//            } catch (Exception e) {
//                log.error("(RABBIT-CONSUMER) - request handler exception: id={}, queue={}, messageId={}",
//                        config.getId(), config.getQueue(), messageId, e);
//                safeNack(channel, message);
//                throw new RabbitConsumerException("receiveAndReply handler exception", e);
//            } finally {
//                MDC.remove(MDC_MESSAGE_ID);
//            }
//        };
//        container.setMessageListener(listener);
//        return container;
//    }
//
//    private static void safeNack(com.rabbitmq.client.Channel channel, Message message) {
//        if (channel == null || !channel.isOpen()) {
//            return;
//        }
//        try {
//            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
//        } catch (Exception ignored) {
//            // Channel might be closed if connection dropped mid-flight.
//        }
//    }
//
//    private static void applyConcurrencyAndPrefetch(SimpleMessageListenerContainer container, ConsumerConfig config) {
//        int c = config.getConcurrency();
//        if (c > 1) {
//            container.setConcurrentConsumers(c);
//            container.setMaxConcurrentConsumers(c);
//        }
//        if (config.getPrefetch() > 0) {
//            container.setPrefetchCount(config.getPrefetch());
//        }
//    }
//
//    private static String headerString(Message message, String name, String fallback) {
//        Object value = message.getMessageProperties().getHeader(name);
//        if (value == null) {
//            return fallback;
//        }
//        return value.toString();
//    }
//
//    private static Map<String, Object> immutableHeaders(Message message) {
//        Map<String, Object> raw = message.getMessageProperties().getHeaders();
//        if (raw == null || raw.isEmpty()) {
//            return Map.of();
//        }
//        return Map.copyOf(new HashMap<>(raw));
//    }
//
//    private static void validateConfig(ConsumerConfig config) {
//        if (config == null) {
//            throw new RabbitConsumerNullArgumentException("config is null");
//        }
//        if (StringUtils.isBlank(config.getId())) {
//            throw new RabbitConsumerNullArgumentException("config.id is blank");
//        }
//        if (StringUtils.isBlank(config.getQueue())) {
//            throw new RabbitConsumerNullArgumentException("config.queue is blank");
//        }
//    }
//
//    private static void validateFactory(SimpleRabbitListenerContainerFactory factory) {
//        if (factory == null) {
//            throw new RabbitConsumerNullArgumentException("factory is null");
//        }
//    }
//}
