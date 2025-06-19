    package com.example.producerback.Listener;


    import jakarta.jms.JMSException;
    import jakarta.jms.Message;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.jms.annotation.JmsListener;
    import org.springframework.jms.core.JmsTemplate;
    import org.springframework.stereotype.Component;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    @Component
    public class DMQListener {

        private static final Logger logger = LoggerFactory.getLogger(DMQListener.class);
        private static final int MAX_RETRIES = 3;
        private final JmsTemplate jmsTemplate;

        @Value("${solace.jms.queue.transaction}")
        private String transactionQueue;

        public DMQListener(JmsTemplate jmsTemplate) {
            this.jmsTemplate = jmsTemplate;
        }
        //Spring Boot uses the @JmsListener annotation to automatically register a consumer that listens to the queue
        /*@JmsListener(destination = "${solace.jms.queue.dmq}")
        public void onMessage(Message jmsMessage) {
            try {
                // Retrieve the message body as a String
                String transactionDetails = jmsMessage.getBody(String.class);

                // Get current retry count; if not set, default to 0
                int retryCount = jmsMessage.propertyExists("retryCount")
                        ? jmsMessage.getIntProperty("retryCount")
                        : 0;

                if (retryCount < MAX_RETRIES) {
                    // Increment the retry count and store in a final variable
                    final int newRetryCount = retryCount + 1;
                    System.out.println("Retrying transaction from DMQ to TransactionQueue (retry count "
                            + newRetryCount + "): " + transactionDetails);
                    // Resend the message with the updated retry count property
                    jmsTemplate.convertAndSend(transactionQueue, transactionDetails, message -> {
                        message.setIntProperty("retryCount", newRetryCount);
                        return message;
                    });
                } else {
                    System.err.println("Message reached maximum retry count (" + MAX_RETRIES
                            + "). Rejecting message: " + transactionDetails);
                    logger.error("Message reached maximum retry count ({}). Manual intervention required for: {}",
                            MAX_RETRIES, transactionDetails);
                    // Optionally, route the message to an error queue for further handling.
                }
            } catch (JMSException e) {
                System.err.println("JMS Exception while processing DMQ message: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error while processing DMQ message: " + e.getMessage());
            }
        }*/
    }
