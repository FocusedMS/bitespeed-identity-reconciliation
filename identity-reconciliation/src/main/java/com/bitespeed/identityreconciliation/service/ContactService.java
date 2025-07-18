package main.java.com.bitespeed.identityreconciliation.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    public Map<String, Object> identifyOrLinkContact(String email, String phoneNumber) {
        List<Contact> matchedContacts = contactRepository.findByEmailAndPhoneNumber(email, phoneNumber);

        if (matchedContacts.isEmpty()){
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

        Contact primary = matchedContacts.stream()
                .filter(c -> c.getLinkprecedence() == LinkPrecedence.PRIMARY)
                .min(Comparator.comparing(Contact::getCreatedAt))
                orElse(matchedContacts.get(0));

        boolean alreadyExists = matchedContacts.stream()
                .anyMatch(c -> Objects.equals(c.getEmail(), email) &&
                             Objects.equals(c.getPhoneNumber(), phoneNumber));

        if(alreadyExists) {
            Contact secondary = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.SECONDARY)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactRepository.save(secondary);
            matchedContacts.add(secondary);
        }

        return buildResponse(matchedContacts);
    }

    private Map<String, Object> buildResponse(List<Contact> contacts) {

        Long primaryId = contacts.stream()
                .filter(c -> c.getLinkprecedence() == LinkPrecedence.PRIMARY)
                .findFirst()
                .map(Contact::getId)
                .orElse(null);

        Set<String> emails = contact.stream()
                .map(Contact::getEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> phoneNumbers = contacts.stream()
                .map(Contact::getPhoneNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> secondaryIds = contacts.stream()
                .filter(c -> c.getLinkprecedence() == LinkPrecedence.SECONDARY)
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
