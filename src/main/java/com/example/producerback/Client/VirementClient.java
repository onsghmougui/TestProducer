package com.example.producerback.Client;

import com.example.common.DTO.VirementDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@FeignClient(name = "virement", url = "http://localhost:8087") // or via Eureka
public interface VirementClient {

    @GetMapping("/api/virements/unpushed")
    List<VirementDTO> getUnpushedVirements();
    @PutMapping("/api/virements/{id}/mark-pushed")
    ResponseEntity<String> markAsPushed(@PathVariable("id") Long id);
}

