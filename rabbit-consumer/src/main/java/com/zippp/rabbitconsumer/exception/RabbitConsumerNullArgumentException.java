package com.zippp.rabbitconsumer.exception;

/**
 * Thrown when a caller-supplied argument is {@code null} or blank.
 *
 * <p>Mirrors the producer's {@code RabbitProducerNullArgumentException} so the
 * two modules feel consistent to anyone reading either side.
 */
public class RabbitConsumerNullArgumentException extends RabbitConsumerException {
    public RabbitConsumerNullArgumentException(String description) {
        super("rabbit_consumer_null_argument", description);
    }
}
