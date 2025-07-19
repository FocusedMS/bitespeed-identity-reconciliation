package com.bitespeed.identityreconciliation.controller;

import com.bitespeed.identityreconciliation.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/identify")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;


    @PostMapping
    public ResponseEntity<Map<String, Object>> identifyContact(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String phoneNumber = request.get("phoneNumber");

        Map<String, Object> contactResponse = contactService.identifyOrLinkContact(email, phoneNumber);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("contact", contactResponse);
        return ResponseEntity.ok(response);
    }
}
