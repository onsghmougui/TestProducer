package com.example.producerback.Config;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolaceConfig {

    /*@Bean
    public ConnectionFactory connectionFactory() throws Exception {
        // Crée la factory
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();

        // Configuration de base
        connectionFactory.setHost("smf://localhost:55555");
        connectionFactory.setVPN("default");
        connectionFactory.setUsername("default");
        connectionFactory.setPassword("default");

        // Transport garanti (pas direct)
        connectionFactory.setDirectTransport(false);

        return connectionFactory;
    }*/
}
