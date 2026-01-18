package com.derso.arquitetura.sagas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

@Configuration
public class RabbitConfig {

    @Value("${sagas.rabbithost}")
    private String rabbitHost;

    @Value("${sagas.rabbituser}")
    private String rabbitUser;

    @Value("${sagas.rabbitpassword}")
    private String rabbitPassword;

    @Bean
    public Connection rabbitConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setUsername(rabbitUser);
        factory.setPassword(rabbitPassword);
        return factory.newConnection();
    }

    @Bean
    public Channel rabbitChannel(Connection connection) throws Exception {
        return connection.createChannel();
    }
}
