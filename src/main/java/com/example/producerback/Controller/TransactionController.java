package com.example.producerback.Controller;

import com.example.producerback.Service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/send")
    public ResponseEntity<String> submitTransaction(@RequestBody String jsonPayload) {
        boolean result = transactionService.sendTransaction(jsonPayload);

        if (result) {
            return ResponseEntity.ok("Transaction accepted and saved for pushing.");
        } else {
            return ResponseEntity.badRequest().body("Invalid transaction. Not saved.");
        }
    }
    @GetMapping("/sync-virements")
    public ResponseEntity<String> syncVirements() {
        transactionService.fetchAndStoreVirements();
        return ResponseEntity.ok("Virements synced to Transaction DB.");
    }


}