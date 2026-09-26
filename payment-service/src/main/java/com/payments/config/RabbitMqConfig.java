package com.payments.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology: topic exchange + durable queues + dead-letter exchange (§11.2).
 */
@Configuration
public class RabbitMqConfig {

    @Value("${app.rabbitmq.exchange:payments.topic}")
    private String exchangeName;

    @Bean
    public TopicExchange paymentsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange("payments.dlx", true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("payments.dead-letter.queue").build();
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable("payments.notifications.queue")
                .withArgument("x-dead-letter-exchange", "payments.dlx")
                .withArgument("x-message-ttl", 604800000)
                .build();
    }

    @Bean
    public Queue fraudCompletedQueue() {
        return QueueBuilder.durable("payments.fraud.completed.queue")
                .withArgument("x-dead-letter-exchange", "payments.dlx")
                .withArgument("x-message-ttl", 604800000)
                .build();
    }

    @Bean
    public Queue fraudFailedQueue() {
        return QueueBuilder.durable("payments.fraud.failed.queue")
                .withArgument("x-dead-letter-exchange", "payments.dlx")
                .withArgument("x-message-ttl", 604800000)
                .build();
    }

    @Bean
    public Binding notificationsCompletedBinding(Queue notificationsQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(paymentsExchange).with("payment.completed");
    }

    @Bean
    public Binding fraudCompletedBinding(Queue fraudCompletedQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(fraudCompletedQueue).to(paymentsExchange).with("payment.completed");
    }

    @Bean
    public Binding fraudFailedBinding(Queue fraudFailedQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(fraudFailedQueue).to(paymentsExchange).with("payment.failed");
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        return template;
    }
}
