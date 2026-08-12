package com.zippp.rabbitconsumer.model;

public record ConsumerParsedMessage<T>(String correlationId, T payload) {

}
