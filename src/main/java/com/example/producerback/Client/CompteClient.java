package com.example.producerback.Client;

import com.example.common.DTO.CompteDTO;
import com.example.common.DTO.UpdateSoldeRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "compte-service", url = "http://localhost:8084/api/v1/accounts") // replace with your compte service URL
public interface CompteClient {
    @GetMapping("/byNumero/{numeroCompte}")
    CompteDTO getByNumeroCompte(@PathVariable String numeroCompte);

    @GetMapping("/compteDto")
    List<CompteDTO> getCompte(@RequestParam String cinUser);
    @PutMapping("/updateSolde")
    ResponseEntity<String> updateSolde(@RequestBody UpdateSoldeRequestDTO request) ;
}
