package com.zippp.rabbitproducer.exception;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class RabbitProducerException extends RuntimeException {

    private final String description;

    public RabbitProducerException(String message, Throwable cause) {
        super(message, cause);
        description = ExceptionUtils.getMessage(cause);
    }

    public RabbitProducerException(String message) {
        super(message);
        description = null;
    }

    public RabbitProducerException(String message, String description) {
        super(message);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
