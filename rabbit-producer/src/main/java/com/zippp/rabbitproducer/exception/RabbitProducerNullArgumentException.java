package com.zippp.rabbitproducer.exception;

public class RabbitProducerNullArgumentException extends RabbitProducerException {
    public RabbitProducerNullArgumentException(String description) {
        super("rabbit_producer_null_argument", description);
    }
}
