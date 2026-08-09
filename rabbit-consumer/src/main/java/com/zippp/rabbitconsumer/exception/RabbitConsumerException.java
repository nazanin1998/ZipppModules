package com.zippp.rabbitconsumer.exception;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Base unchecked exception for every failure originating inside the
 * {@code RabbitConsumer} module. Mirrors the producer's exception model:
 * keeps the original cause so callers can log the underlying broker /
 * deserialization stack trace.
 */
public class RabbitConsumerException extends RuntimeException {

    private final String description;

    public RabbitConsumerException(String message, Throwable cause) {
        super(message, cause);
        description = ExceptionUtils.getMessage(cause);
    }

    public RabbitConsumerException(String message) {
        super(message);
        description = null;
    }

    public RabbitConsumerException(String message, String description) {
        super(message);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
