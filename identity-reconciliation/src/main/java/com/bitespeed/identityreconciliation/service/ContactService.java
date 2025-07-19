package com.bitespeed.identityreconciliation.service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.bitespeed.identityreconciliation.repository.ContactRepository;
import com.bitespeed.identityreconciliation.model.Contact;
import com.bitespeed.identityreconciliation.model.LinkPrecedence;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    public Map<String, Object> identifyOrLinkContact(String email, String phoneNumber) {
        List<Contact> matchedContacts = contactRepository.findByEmailOrPhoneNumber(email, phoneNumber);

        if (matchedContacts.isEmpty()) {
            Contact newContact = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkPrecedence(LinkPrecedence.PRIMARY)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactRepository.save(newContact);
            return buildResponse(Collections.singletonList(newContact));
        }

        // Find all unique primaries among matched contacts
        List<Contact> primaries = matchedContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .collect(Collectors.toList());

        // Find the oldest primary
        Contact oldestPrimary = primaries.stream()
                .min(Comparator.comparing(Contact::getCreatedAt))
                .orElse(matchedContacts.get(0));

        // If there are multiple primaries, demote the newer ones and their secondaries
        for (Contact primary : primaries) {
            if (!primary.getId().equals(oldestPrimary.getId())) {
                // Demote this primary
                primary.setLinkPrecedence(LinkPrecedence.SECONDARY);
                primary.setLinkedId(oldestPrimary.getId().toString());
                primary.setUpdatedAt(LocalDateTime.now());
                contactRepository.save(primary);
                // Demote all its secondaries
                List<Contact> secondaries = contactRepository.findAllByLinkedIdOrId(primary.getId());
                for (Contact sec : secondaries) {
                    if (!sec.getId().equals(primary.getId())) { // avoid double demotion
                        sec.setLinkedId(oldestPrimary.getId().toString());
                        sec.setUpdatedAt(LocalDateTime.now());
                        contactRepository.save(sec);
                    }
                }
            }
        }

        boolean alreadyExists = matchedContacts.stream()
                .anyMatch(c ->
                        Objects.equals(c.getEmail(), email) &&
                        Objects.equals(c.getPhoneNumber(), phoneNumber)
                );

        if (!alreadyExists) {
            Contact secondary = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkedId(oldestPrimary.getId().toString())
                    .linkPrecedence(LinkPrecedence.SECONDARY)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactRepository.save(secondary);
            matchedContacts.add(secondary);
        }

        List<Contact> allRelated = contactRepository.findAllByLinkedIdOrId(oldestPrimary.getId());
        // Add the oldest primary itself
        allRelated.add(oldestPrimary);

        return buildResponse(allRelated);
    }

    private Map<String, Object> buildResponse(List<Contact> contacts) {
        Long primaryId = contacts.stream()
                .map(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY
                        ? c.getId()
                        : Long.valueOf(c.getLinkedId()))
                .min(Long::compareTo)
                .orElse(null);

        Set<String> emails = contacts.stream()
                .map(Contact::getEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> phoneNumbers = contacts.stream()
                .map(Contact::getPhoneNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> secondaryIds = contacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.SECONDARY)
                .map(Contact::getId)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("primaryContactId", primaryId);
        response.put("emails", emails);
        response.put("phoneNumbers", phoneNumbers);
        response.put("secondaryContactIds", secondaryIds);

        return response;
    }
}
