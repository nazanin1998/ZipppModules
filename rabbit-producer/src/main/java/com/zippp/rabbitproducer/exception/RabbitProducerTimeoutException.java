package com.zippp.rabbitproducer.exception;

public class RabbitProducerTimeoutException extends RabbitProducerException {
    public RabbitProducerTimeoutException() {
        super("rabbit_producer_timeout");
    }
}
