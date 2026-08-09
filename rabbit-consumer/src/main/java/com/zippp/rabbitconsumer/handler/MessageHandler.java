package com.zippp.rabbitconsumer.handler;

import java.util.Map;

/**
 * Callback the target project supplies to {@link com.zippp.rabbitconsumer.consumer.MessageConsumer#receive}.
 *
 * <p>The library takes care of subscription, deserialization, MDC correlation,
 * acknowledgement, and exception translation. The handler only needs to contain
 * the business logic that should run for each delivered message.
 *
 * @param <T> deserialized payload type (the same way {@code MessageProducer}
 *            treats payloads as {@code Object}, the type is a caller-side hint)
 */
@FunctionalInterface
public interface MessageHandler<T> {

    /**
     * Invoked once per delivered message.
     *
     * @param payload  the deserialized message body
     * @param headers  AMQP headers attached to the message (immutable snapshot)
     * @throws Exception any thrown exception is translated by the library into
     *                   a {@link com.zippp.rabbitconsumer.exception.RabbitConsumerException}
     *                   and surfaces through the consumer's logging — the message
     *                   is nacked so the broker can redeliver / dead-letter it.
     */
    void onMessage(T payload, Map<String, Object> headers) throws Exception;
}
