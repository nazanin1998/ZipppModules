package com.zippp.rabbitproducer.producer;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

/**
 * Contract for publishing messages to RabbitMQ.
 *
 * <p>Defined here (in the {@code RabbitProducer} module) so any future microservice
 * that wants to produce messages can drop in a dependency on
 * {@code com.zippp:rabbit-producer} and inject this interface — without needing to
 * know about RabbitMQ specifics (exchanges, routing keys, template, etc.).
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code exchange} and {@code routingKey} are passed per call so the same
 *       producer can target multiple destinations without reconfiguration.</li>
 *   <li>{@code payload} is {@code Object} — serialization is the caller's contract
 *       with the consumer. In practice this will be a Jackson-serializable POJO.</li>
 *   <li>{@link #sendAndReceive} uses Spring AMQP's reply pattern via
 *       {@code RabbitTemplate.convertSendAndReceiveAsType}; the reply is
 *       deserialized according to {@link ParameterizedTypeReference}, which
 *       preserves generic type info (works for collections and nested generics,
 *       unlike {@code Class<?>}).</li>
 *   <li>Every publish is tagged with a generated {@code messageId}. The id is
 *       attached to the AMQP message header {@code x-message-id} (so consumers
 *       can correlate logs) and put on SLF4J's MDC under {@code messageId} for
 *       the duration of the publish call (so producer logs are correlatable too).</li>
 * </ul>
 */
public interface MessageProducer {

    /** AMQP header carrying the producer-generated correlation id. */
    String HEADER_MESSAGE_ID = "x-message-id";

    /**
     * Fire-and-forget publish. Returns once the message has been handed to the
     * broker; does not wait for a consumer to acknowledge.
     *
     * @param exchange    target exchange (e.g. {@code ""} for the default exchange,
     *                    or a named exchange declared on the broker)
     * @param routingKey  routing key used by the exchange to dispatch
     * @param payload     message body; serialized by the configured message converter
     * @throws com.zippp.rabbitproducer.exception.RabbitProducerNullArgumentException
     *         if any of the arguments are blank or null
     * @throws com.zippp.rabbitproducer.exception.RabbitProducerException
     *         on broker / serialization failure
     */
    void send(String exchange, String routingKey, Object payload);

    /**
     * Fire-and-forget publish with extra AMQP headers.
     *
     * <p>The provided headers are merged with the producer's own metadata
     * ({@link #HEADER_MESSAGE_ID}). Producer-owned headers take precedence —
     * caller-supplied keys with the same name are overwritten.
     *
     * @param exchange    target exchange
     * @param routingKey  routing key
     * @param payload     message body
     * @param headers     extra AMQP headers; may be {@code null} or empty
     */
    void send(String exchange, String routingKey, Object payload, Map<String, Object> headers);

    /**
     * Fire-and-forget publish with a custom {@link MessagePostProcessor}.
     *
     * <p>Use this when you need full control over the outgoing AMQP message
     * (e.g. setting {@code content_type}, attaching a {@code timestamp}, or
     * rewriting the body). The producer still injects its correlation id unless
     * the post-processor overrides it.
     *
     * @param exchange    target exchange
     * @param routingKey  routing key
     * @param payload     message body
     * @param postProcessor post-processor applied to the outgoing message; must not be {@code null}
     */
    void send(String exchange, String routingKey, Object payload, MessagePostProcessor postProcessor);

    /**
     * Publish-and-await-reply. Sends the message, blocks the calling thread until a
     * reply is received (or the configured reply timeout elapses), and returns the
     * deserialized reply payload.
     *
     * <p>Requires a reply queue / {@code RabbitTemplate.setReplyTimeout} configured
     * on the broker side. See {@code RabbitConfig}.
     *
     * @param exchange        target exchange
     * @param routingKey      routing key
     * @param payload         request body
     * @param responseType    type reference for deserializing the reply; use
     *                        {@code ParameterizedTypeReference} for generic types
     *                        (e.g. {@code List<Order>})
     * @param <R>             reply type
     * @return the reply payload
     * @throws com.zippp.rabbitproducer.exception.RabbitProducerTimeoutException
     *         if the reply did not arrive within the configured timeout
     */
    <R> R sendAndReceive(String exchange, String routingKey, Object payload, ParameterizedTypeReference<R> responseType);

    /**
     * Publish-and-await-reply with extra AMQP headers. See
     * {@link #send(String, String, Object, Map)} for header-precedence rules.
     */
    <R> R sendAndReceive(String exchange, String routingKey, Object payload,
                         Map<String, Object> headers,
                         ParameterizedTypeReference<R> responseType);
}