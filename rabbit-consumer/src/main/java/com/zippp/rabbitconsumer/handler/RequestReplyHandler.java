package com.zippp.rabbitconsumer.handler;

import java.util.Map;

/**
 * Callback the target project supplies to
 * {@link com.zippp.rabbitconsumer.consumer.MessageConsumer#receiveAndReply}.
 *
 * <p>Symmetric to {@link MessageHandler}, but the return value of
 * {@link #onRequest(Object, Map)} is sent back to the producer as the AMQP
 * reply. Used to implement request/reply flows where the consumer responds to
 * a single message rather than publishing its own.
 *
 * @param <T> deserialized request type
 * @param <R> reply type that will be JSON-serialized and sent back
 */
@FunctionalInterface
public interface RequestReplyHandler<T, R> {

    /**
     * Invoked once per delivered request message.
     *
     * @param payload  the deserialized request body
     * @param headers  AMQP headers attached to the request
     * @return the reply payload; serialized by the configured message converter
     *         and sent back to the producer. Returning {@code null} is permitted
     *         and produces no reply.
     * @throws Exception any thrown exception is translated into a
     *                   {@link com.zippp.rabbitconsumer.exception.RabbitConsumerException}
     *                   and logged; the original request is nacked.
     */
    R onRequest(T payload, Map<String, Object> headers) throws Exception;
}
