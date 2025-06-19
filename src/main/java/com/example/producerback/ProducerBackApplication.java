package com.example.producerback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.connection.CachingConnectionFactory;

import jakarta.jms.ConnectionFactory;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class ProducerBackApplication {

    public static void main(String[] args) {

        SpringApplication.run(ProducerBackApplication.class, args);
    }

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        CachingConnectionFactory ccf = new CachingConnectionFactory();
        ccf.setTargetConnectionFactory(connectionFactory); // Use the configured ConnectionFactory
        jmsTemplate.setConnectionFactory(ccf);
        jmsTemplate.setPubSubDomain(false); // false for queues, true for topics
        jmsTemplate.setSessionTransacted(false);  // Disable transacted sessions for direct transport
        jmsTemplate.setDeliveryPersistent(true);

        jmsTemplate.setExplicitQosEnabled(true);


    }

}
