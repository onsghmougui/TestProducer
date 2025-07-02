package com.example.producerback;

import com.example.producerback.Entity.Transaction;
import com.example.producerback.Entity.TransactionType;
import com.example.producerback.Repository.TransactionRepository;
import com.example.producerback.Service.TransactionPushScheduler;
import com.example.producerback.util.CryptoUtil;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class TransactionPushSchedulerTest {

    TransactionRepository repo;
    JmsTemplate jmsTemplate;
    TransactionPushScheduler scheduler;

    @BeforeEach
    void setUp() {
        repo = mock(TransactionRepository.class);
        jmsTemplate = mock(JmsTemplate.class);

        // note: uses the real ObjectMapper from the service constructor
        scheduler = new TransactionPushScheduler(repo, jmsTemplate);

        // inject the queue name via reflection
        ReflectionTestUtils.setField(
                scheduler,                 // target object
                "transactionQueue",        // name of the private field
                "TX_QUEUE"                 // the value to inject
        );
    }

    @Test
    void pushPendingTransactions_happyPath_publishesAndMarksPushed() throws Exception {
        // 1) prepare one pending transaction
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setUserIdentityNumber("CIN123");
        tx.setSender("ACC1");
        tx.setReceiver("ACC2");
        tx.setReference("REF-1");
        tx.setTransactionType(TransactionType.CREDIT);
        tx.setTimestamp(LocalDateTime.now());
        tx.setPushed(false);
        tx.setLogs("initial");

        when(repo.findByPushedFalse()).thenReturn(List.of(tx));

        // 2) stub static CryptoUtil.encrypt(...)
        try (MockedStatic<CryptoUtil> crypto = Mockito.mockStatic(CryptoUtil.class)) {
            // capture the JSON passed in, then return an "encrypted" wrapper
            crypto.when(() -> CryptoUtil.encrypt(anyString()))
                    .thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");

            // 3) prepare a fake JMS Session to satisfy the callback
            Session mockSession = mock(Session.class);
            TextMessage mockMsg = mock(TextMessage.class);
            Queue mockQueue = mock(Queue.class);
            var mockProducer = mock(jakarta.jms.MessageProducer.class);

            when(mockSession.createTextMessage(startsWith("ENC("))).thenReturn(mockMsg);
            when(mockSession.createQueue("TX_QUEUE")).thenReturn(mockQueue);
            when(mockSession.createProducer(mockQueue)).thenReturn(mockProducer);

            // 4) stub jmsTemplate.execute(...) to invoke the lambda with our mockSession
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                SessionCallback<?> callback = inv.getArgument(0);
                return callback.doInJms(mockSession);
            }).when(jmsTemplate).execute(any(SessionCallback.class), eq(true));

            // 5) call the scheduler
            scheduler.pushPendingTransactions();
        }

        // 6) verify that the tx object was updated
        assertTrue(tx.getPushed(), "`pushed` should be set to true");
        assertEquals("Successfully pushed to broker", tx.getLogs());

        // 7) verify it was saved back to the repo
        verify(repo).save(tx);
    }

    @Test
    void pushPendingTransactions_whenJmsFails_logsFailureAndStillSaves() throws Exception {
        Transaction tx = new Transaction();
        tx.setId(2L);
        tx.setUserIdentityNumber("CIN999");
        tx.setSender("A");
        tx.setReceiver("B");
        tx.setReference("ERR");
        tx.setTransactionType(TransactionType.CREDIT);
        tx.setTimestamp(LocalDateTime.now());
        tx.setPushed(false);
        tx.setLogs("initial");

        when(repo.findByPushedFalse()).thenReturn(List.of(tx));

        // stub encryption
        try (MockedStatic<CryptoUtil> crypto = Mockito.mockStatic(CryptoUtil.class)) {
            crypto.when(() -> CryptoUtil.encrypt(anyString()))
                    .thenReturn("BAD");

            // Make jmsTemplate.execute(...) throw
            doThrow(new RuntimeException("JMS down"))
                    .when(jmsTemplate).execute(any(SessionCallback.class), eq(true));

            scheduler.pushPendingTransactions();
        }

        // Even on JMS error, pushed remains false, but logs updated
        assertFalse(tx.getPushed());
        assertTrue(tx.getLogs().startsWith("Push failed: JMS down"));

        verify(repo).save(tx);
    }
}
