package com.example.producerback.Service;

import com.example.producerback.Entity.Transaction;
import com.example.producerback.Repository.TransactionRepository;
import com.example.producerback.util.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solacesystems.jms.SupportedProperty;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
@EnableScheduling
@Service
public class TransactionPushScheduler {

    private final TransactionRepository transactionRepository;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public TransactionPushScheduler(TransactionRepository transactionRepository, JmsTemplate jmsTemplate) {
        this.transactionRepository = transactionRepository;
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // optional, for better formatting
    }


    @Value("${solace.jms.queue.transaction}")
    private String transactionQueue;


    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pushPendingTransactions() {
        List<Transaction> pendingTransactions = transactionRepository.findByPushedFalse();

        for (Transaction txn : pendingTransactions) {
            try {
                String json = objectMapper.writeValueAsString(txn);
                String encryptedJson = CryptoUtil.encrypt(json); // 👈 encryption step
                //just for testing
                //System.out.println("🔐 Encrypted message: " + encryptedJson);
                //set the priority dynamically
                jmsTemplate.execute(session -> {
                    jakarta.jms.TextMessage message = session.createTextMessage(encryptedJson); // 👈 encrypted message
                    jakarta.jms.MessageProducer producer = session.createProducer(session.createQueue(transactionQueue));
                    producer.setPriority(txn.getTransactionType().getPriority());
                    producer.setDeliveryMode(jakarta.jms.DeliveryMode.PERSISTENT);
                    message.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_DEAD_MSG_QUEUE_ELIGIBLE, true);
                    producer.send(message);
                    producer.close();
                    return null;
                }, true);
                txn.setPushed(true);
                txn.setLogs("Successfully pushed to broker");
            } catch (Exception e) {
                txn.setLogs("Push failed: " + e.getMessage());
            }
            transactionRepository.save(txn); // Save regardless to update logs
        }
    }
}
