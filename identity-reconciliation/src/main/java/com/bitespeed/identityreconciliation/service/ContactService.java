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
        // Find contacts that exactly match the email or phone from the request
        List<Contact> matchedContacts = contactRepository.findByEmailOrPhoneNumber(email, phoneNumber);

        if (matchedContacts.isEmpty()) {
            // No existing contacts found, create a new primary contact
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
        Set<Long> primaryIds = new HashSet<>();
        List<Contact> primaries = new ArrayList<>();
        
        for (Contact contact : matchedContacts) {
            if (contact.getLinkPrecedence() == LinkPrecedence.PRIMARY) {
                primaryIds.add(contact.getId());
                primaries.add(contact);
            } else if (contact.getLinkedId() != null) {
                primaryIds.add(Long.valueOf(contact.getLinkedId()));
            }
        }

        // Get all primary contacts
        List<Contact> allPrimaries = contactRepository.findAllById(primaryIds);
        
        // Find the oldest primary
        Contact oldestPrimary = allPrimaries.stream()
                .min(Comparator.comparing(Contact::getCreatedAt))
                .orElse(matchedContacts.get(0));

        // If there are multiple primaries, demote the newer ones and their secondaries
        for (Contact primary : allPrimaries) {
            if (!primary.getId().equals(oldestPrimary.getId())) {
                // Demote this primary
                primary.setLinkPrecedence(LinkPrecedence.SECONDARY);
                primary.setLinkedId(oldestPrimary.getId().toString());
                primary.setUpdatedAt(LocalDateTime.now());
                contactRepository.save(primary);
                
                // Demote all its secondaries
                List<Contact> secondaries = contactRepository.findAllByLinkedIdOrId(primary.getId());
                for (Contact sec : secondaries) {
                    if (!sec.getId().equals(primary.getId()) && sec.getLinkPrecedence() == LinkPrecedence.SECONDARY) {
                        sec.setLinkedId(oldestPrimary.getId().toString());
                        sec.setUpdatedAt(LocalDateTime.now());
                        contactRepository.save(sec);
                    }
                }
            }
        }

        // Check if the exact email and phone combination already exists
        boolean alreadyExists = matchedContacts.stream()
                .anyMatch(c ->
                        Objects.equals(c.getEmail(), email) &&
                        Objects.equals(c.getPhoneNumber(), phoneNumber)
                );

        if (!alreadyExists) {
            // Create a new secondary contact
            Contact secondary = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkedId(oldestPrimary.getId().toString())
                    .linkPrecedence(LinkPrecedence.SECONDARY)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactRepository.save(secondary);
        }

        // Get all contacts linked to the oldest primary
        List<Contact> allRelated = contactRepository.findAllByLinkedIdOrId(oldestPrimary.getId());
        // Add the oldest primary itself if not already included
        if (!allRelated.stream().anyMatch(c -> c.getId().equals(oldestPrimary.getId()))) {
            allRelated.add(oldestPrimary);
        }

        return buildResponse(allRelated);
    }

    private Map<String, Object> buildResponse(List<Contact> contacts) {
        // Find the primary contact
        Contact primaryContact = contacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .findFirst()
                .orElse(contacts.get(0));

        // Get all unique emails and phone numbers
        Set<String> emails = contacts.stream()
                .map(Contact::getEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> phoneNumbers = contacts.stream()
                .map(Contact::getPhoneNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Get secondary contact IDs
        List<Long> secondaryIds = contacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.SECONDARY)
                .map(Contact::getId)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("primaryContactId", primaryContact.getId());
        response.put("emails", new ArrayList<>(emails));
        response.put("phoneNumbers", new ArrayList<>(phoneNumbers));
        response.put("secondaryContactIds", secondaryIds);

        return response;
    }
}
