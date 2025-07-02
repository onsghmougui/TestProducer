package com.example.producerback.Service;

import com.example.common.DTO.UpdateSoldeRequestDTO;
import com.example.common.DTO.VirementDTO;
import com.example.producerback.Client.CompteClient;
import com.example.producerback.Client.VirementClient;
import com.example.producerback.Entity.Transaction;
import com.example.producerback.Entity.TransactionType;
import com.example.producerback.Repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    @Autowired
    private JmsTemplate jmsTemplate;

    @Value("${solace.jms.queue.transaction}")
    private String transactionQueue;

    @Value("${solace.jms.queue.dmq}")
    private String dmqQueue;

    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson for parsing JSON
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private static final String FIELD_DEBIT = "debit";
    private static final String FIELD_CREDIT = "credit";
    private static final String FIELD_TRANSACTION_TYPE = "transactionType";


    private final VirementClient virementClient;
    private final CompteClient compteClient;
    public TransactionService(TransactionRepository transactionRepository,VirementClient virementClient , CompteClient compteClient) {
        this.transactionRepository = transactionRepository;
        this.virementClient = virementClient;
        this.compteClient = compteClient;
    }

    public boolean sendTransaction(String transactionDetails) {
        try {
            JsonNode jsonNode = objectMapper.readTree(transactionDetails);

            StringBuilder logs = new StringBuilder();
            boolean isValid = validateTransaction(jsonNode, logs);

            if (!isValid) {
                logger.warn("Transaction invalid. Not saved: {}", logs);
                return false;
            }

            Transaction transaction = new Transaction();
            transaction.setUserIdentityNumber(jsonNode.path("userIdentityNumber").asText());
            transaction.setSender(jsonNode.path("sender").asText());
            transaction.setReceiver(jsonNode.path("receiver").asText());
            transaction.setDebit(jsonNode.path(FIELD_DEBIT).asDouble(0));
            transaction.setCredit(jsonNode.path(FIELD_CREDIT).asDouble(0));
            transaction.setDescription(jsonNode.path("description").asText());
            transaction.setReference(jsonNode.path("reference").asText());
            transaction.setTimestamp(LocalDateTime.now());
            transaction.setPushed(false);
            transaction.setLogs("pending push");

            if (jsonNode.has(FIELD_TRANSACTION_TYPE)) {
                try {
                    TransactionType type = TransactionType.valueOf(jsonNode.get(FIELD_TRANSACTION_TYPE).asText());
                    transaction.setTransactionType(type);
                } catch (IllegalArgumentException e) {
                    transaction.setTransactionType(null);
                }
            }

            transactionRepository.save(transaction);
            updateSoldeIfNecessary(transaction);

            logger.info("Transaction saved with pushed=false, awaiting broker push.");
            return true;

        } catch (Exception e) {
            logger.error("Exception while processing transaction: {}", e.getMessage(), e);
            return false;
        }
    }

    private void updateSoldeIfNecessary(Transaction transaction) {
        TransactionType type = transaction.getTransactionType();
        if (type == TransactionType.CREDIT ||
                type == TransactionType.PAIEMENT_FACTURE ||
                type == TransactionType.PAIEMENT_PAR_CARTE) {

            double debitAmount = transaction.getDebit();
            if (debitAmount > 0) {
                UpdateSoldeRequestDTO updateRequest = new UpdateSoldeRequestDTO(
                        transaction.getSender(), -debitAmount
                );
                try {
                    compteClient.updateSolde(updateRequest);
                    logger.info("Solde débité pour le compte {}", transaction.getSender());
                } catch (Exception e) {
                    logger.error("Erreur lors du débit du solde : {}", e.getMessage(), e);
                }
            }
        }
    }

    private boolean validateTransaction(JsonNode jsonNode, StringBuilder logs) {
        boolean valid = true;

        if (isEmpty(jsonNode, "userIdentityNumber")) {
            logs.append("Missing userIdentityNumber. ");
            valid = false;
        }

        if (isEmpty(jsonNode, "sender")) {
            logs.append("Missing sender. ");
            valid = false;
        }

        String type = jsonNode.has(FIELD_TRANSACTION_TYPE) ? jsonNode.get(FIELD_TRANSACTION_TYPE).asText() : "";

        if (isEmpty(jsonNode, "receiver") &&
                !(type.equals("PAIEMENT_FACTURE") || type.equals("PAIEMENT_PAR_CARTE") || type.equals("CREDIT"))) {
            logs.append("Missing receiver. ");
            valid = false;
        }

        double debit = jsonNode.has(FIELD_DEBIT) ? jsonNode.get(FIELD_DEBIT).asDouble(0) : 0;
        double credit = jsonNode.has(FIELD_CREDIT) ? jsonNode.get(FIELD_CREDIT).asDouble(0) : 0;

        if (debit <= 0 && credit <= 0 &&
                !(type.equals("PAIEMENT_FACTURE") || type.equals("PAIEMENT_PAR_CARTE") || type.equals("CREDIT"))) {
            logs.append("Both debit and credit amounts are invalid or missing. ");
            valid = false;
        }

        if (!jsonNode.has(FIELD_TRANSACTION_TYPE) || !isValidTransactionType(type)) {
            logs.append("Invalid or missing transactionType. ");
            valid = false;
        }

        return valid;
    }

    private boolean isEmpty(JsonNode node, String field) {
        return !node.has(field) || node.get(field).asText().trim().isEmpty();
    }

    private boolean isValidTransactionType(String type) {
        try {
            TransactionType.valueOf(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void fetchAndStoreVirements() {
        List<VirementDTO> virements = virementClient.getUnpushedVirements();
        for (VirementDTO dto : virements) {
            Transaction t = new Transaction();
            t.setUserIdentityNumber(dto.getUserIdentityNumber());
            t.setTransactionType(TransactionType.valueOf(dto.getTransactionType()));
            t.setSender(dto.getSender());
            t.setReceiver(dto.getReceiver());
            t.setDebit(dto.getAmount());
            t.setCredit(dto.getAmount());
            t.setDescription(dto.getDescription());
            t.setReference(dto.getReference());
            t.setTimestamp(dto.getTimestamp());
            t.setPushed(false);
            transactionRepository.save(t);
            virementClient.markAsPushed(dto.getId());
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void autoSyncVirements() {
        fetchAndStoreVirements();
    }


}

