package com.example.producerback;

import com.example.producerback.Entity.TransactionType;
import com.example.common.DTO.UpdateSoldeRequestDTO;
import com.example.common.DTO.VirementDTO;
import com.example.producerback.Client.CompteClient;
import com.example.producerback.Client.VirementClient;
import com.example.producerback.Entity.Transaction;
import com.example.producerback.Repository.TransactionRepository;
import com.example.producerback.Service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        locations = "classpath:application-test.properties")
@Transactional
class TransactionServiceTest {

    @Autowired
    private TransactionService service;

    @Autowired
    private TransactionRepository repo;

    @MockitoBean
    private CompteClient compteClient;

    @MockitoBean
    private VirementClient virementClient;


    @BeforeEach
    void cleanup() {
        repo.deleteAll();
        reset(compteClient, virementClient);
    }

    @Test
    void sendTransaction_validCredit_savesAndUpdatesSolde() {
        // given a well-formed CREDIT transaction JSON
        String json = """
            {
              "userIdentityNumber":"CIN42",
              "sender":"ACC1",
              "receiver":"ACC2",
              "debit":100.0,
              "credit":0.0,
              "description":"Test",
              "reference":"REF42",
              "transactionType":"CREDIT"
            }
            """;

        // stub the non-void updateSolde(...) to return null
        when(compteClient.updateSolde(any(UpdateSoldeRequestDTO.class)))
                .thenReturn(null);

        // when
        boolean result = service.sendTransaction(json);

        // then
        assertTrue(result, "Valid CREDIT should return true");
        List<Transaction> list = repo.findAll();
        assertEquals(1, list.size(), "One transaction should be saved");

        Transaction tx = list.get(0);
        assertEquals("CIN42", tx.getUserIdentityNumber());
        assertEquals(100.0, tx.getDebit());
        assertEquals(TransactionType.CREDIT, tx.getTransactionType());
        assertFalse(tx.getPushed(), "pushed flag should remain false on initial save");

        // and updateSolde is called exactly once with negative debit
        verify(compteClient, times(1))
                .updateSolde(new UpdateSoldeRequestDTO("ACC1", -100.0));
    }


    @Test
    void sendTransaction_missingMandatory_failsValidation() {
        // missing userIdentityNumber and sender
        String bad = """
            {
              "receiver":"ACC2",
              "debit":0.0,
              "credit":50.0,
              "transactionType":"CREDIT"
            }
            """;

        boolean result = service.sendTransaction(bad);

        assertThat(result).isFalse();
        assertThat(repo.findAll()).isEmpty();
        verifyNoInteractions(compteClient);
    }

    @Test
    void sendTransaction_invalidType_failsValidation() {
        // invalid transactionType should trigger validation failure
        String json = """
            {
              "userIdentityNumber":"CINX",
              "sender":"ACC9",
              "receiver":"ACC8",
              "debit":10.0,
              "credit":0.0,
              "description":"desc",
              "reference":"R9",
              "transactionType":"FOOBAR"
            }
            """;

        boolean result = service.sendTransaction(json);

        assertFalse(result, "Unknown transactionType should return false");
        assertTrue(repo.findAll().isEmpty(), "No transaction should be persisted");
        verifyNoInteractions(compteClient);
    }

    @Test
    void fetchAndStoreVirements_savesAllAndMarksPushed() {
        // given two VirementDTOs from remote
        VirementDTO v1 = new VirementDTO();
        v1.setId(101L);
        v1.setUserIdentityNumber("CIN1");
        v1.setSender("S1");
        v1.setReceiver("R1");
        v1.setAmount(25.0);
        v1.setDescription("V1");
        v1.setReference("VREF1");
        v1.setTimestamp(LocalDateTime.now().minusDays(1));
        v1.setTransactionType("CREDIT");              // ← add this

        VirementDTO v2 = new VirementDTO();
        v2.setId(202L);
        v2.setUserIdentityNumber("CIN2");
        v2.setSender("S2");
        v2.setReceiver("R2");
        v2.setAmount(50.0);
        v2.setDescription("V2");
        v2.setReference("VREF2");
        v2.setTimestamp(LocalDateTime.now().minusHours(5));
        v2.setTransactionType("PAIEMENT_FACTURE");     // ← and this

        when(virementClient.getUnpushedVirements()).thenReturn(List.of(v1, v2));
        when(virementClient.markAsPushed(anyLong())).thenReturn(null);

        // when
        service.fetchAndStoreVirements();

        // then both persisted
        List<Transaction> txs = repo.findAll();
        assertEquals(2, txs.size(), "Should have imported two virements");
        List<String> refs = txs.stream().map(Transaction::getReference).toList();
        assertTrue(refs.containsAll(List.of("VREF1", "VREF2")));

        // and each DTO is marked pushed once
        verify(virementClient).markAsPushed(101L);
        verify(virementClient).markAsPushed(202L);
    }

}
