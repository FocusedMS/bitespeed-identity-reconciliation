package main.java.com.bitespeed.identityreconciliation.controller;

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

        Map<String, Object> response = contactService.identifyOrLinkContact(email, phoneNumber);
        return ResponseEntity.ok(response);
    }
}
