package com.bitespeed.identityreconciliation;

import com.bitespeed.identityreconciliation.model.Contact;
import com.bitespeed.identityreconciliation.model.LinkPrecedence;
import com.bitespeed.identityreconciliation.repository.ContactRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.persistence.EntityManager;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ContactIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        contactRepository.deleteAll();
        // Reset PostgreSQL sequence for predictable IDs
        entityManager.createNativeQuery("ALTER SEQUENCE contact_id_seq RESTART WITH 1").executeUpdate();
    }

    // Helper to fetch all contacts matching either email or phone number, handling nulls
    private java.util.List<Contact> findContactsByEmailOrPhone(String email, String phone) {
        if (email != null && phone != null) {
            return contactRepository.findByEmailOrPhoneNumber(email, phone);
        } else if (email != null) {
            return contactRepository.findByEmailOrPhoneNumber(email, "");
        } else if (phone != null) {
            return contactRepository.findByEmailOrPhoneNumber("", phone);
        } else {
            return java.util.Collections.emptyList();
        }
    }

    @Test
    void testNewContactIsCreatedAsPrimary() throws Exception {
        String json = """
            { "email": "doc@future.com", "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        // Extract primaryContactId from response
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        Assertions.assertTrue(primaryContactId > 0);
    }

    @Test
    void testDuplicateRequestReturnsSameContact() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": "doc@future.com", "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        // Fetch all contacts and apply service logic
        var allContacts = findContactsByEmailOrPhone("doc@future.com", "9999999999");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
        java.util.List<Integer> secondaryIds = com.jayway.jsonpath.JsonPath.read(response, "$.contact.secondaryContactIds");
        java.util.List<Integer> expectedSecondaries = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.SECONDARY)
                .map(Contact::getId)
                .map(Long::intValue)
                .collect(java.util.stream.Collectors.toList());
        org.junit.jupiter.api.Assertions.assertEquals(expectedSecondaries.size(), secondaryIds.size());
        org.junit.jupiter.api.Assertions.assertTrue(secondaryIds.containsAll(expectedSecondaries));
    }

    @Test
    void testExistingPhoneNewEmailCreatesSecondary() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": "emmett@future.com", "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone("emmett@future.com", "9999999999");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
        java.util.List<String> emails = com.jayway.jsonpath.JsonPath.read(response, "$.contact.emails");
        org.junit.jupiter.api.Assertions.assertTrue(emails.contains("doc@future.com"));
        org.junit.jupiter.api.Assertions.assertTrue(emails.contains("emmett@future.com"));
        java.util.List<Integer> secondaryIds = com.jayway.jsonpath.JsonPath.read(response, "$.contact.secondaryContactIds");
        java.util.List<Integer> expectedSecondaries = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.SECONDARY)
                .map(Contact::getId)
                .map(Long::intValue)
                .collect(java.util.stream.Collectors.toList());
        org.junit.jupiter.api.Assertions.assertEquals(expectedSecondaries.size(), secondaryIds.size());
        org.junit.jupiter.api.Assertions.assertTrue(secondaryIds.containsAll(expectedSecondaries));
    }

    @Test
    void testExistingEmailNewPhoneCreatesSecondary() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": "doc@future.com", "phoneNumber": "8888888888" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone("doc@future.com", "8888888888");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
        java.util.List<String> phoneNumbers = com.jayway.jsonpath.JsonPath.read(response, "$.contact.phoneNumbers");
        org.junit.jupiter.api.Assertions.assertTrue(phoneNumbers.contains("9999999999"));
        org.junit.jupiter.api.Assertions.assertTrue(phoneNumbers.contains("8888888888"));
        java.util.List<Integer> secondaryIds = com.jayway.jsonpath.JsonPath.read(response, "$.contact.secondaryContactIds");
        java.util.List<Integer> expectedSecondaries = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.SECONDARY)
                .map(Contact::getId)
                .map(Long::intValue)
                .collect(java.util.stream.Collectors.toList());
        org.junit.jupiter.api.Assertions.assertEquals(expectedSecondaries.size(), secondaryIds.size());
        org.junit.jupiter.api.Assertions.assertTrue(secondaryIds.containsAll(expectedSecondaries));
    }

    @Test
    void testExistingEmailOnly() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": "doc@future.com" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone("doc@future.com", null);
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
        java.util.List<String> emails = com.jayway.jsonpath.JsonPath.read(response, "$.contact.emails");
        org.junit.jupiter.api.Assertions.assertTrue(emails.contains("doc@future.com"));
    }

    @Test
    void testExistingPhoneOnly() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone(null, "9999999999");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
        java.util.List<String> phoneNumbers = com.jayway.jsonpath.JsonPath.read(response, "$.contact.phoneNumbers");
        org.junit.jupiter.api.Assertions.assertTrue(phoneNumbers.contains("9999999999"));
    }

    @Test
    void testMergeTwoPrimaries() throws Exception {
        Contact primary1 = contactRepository.save(Contact.builder()
                .email("one@flux.com")
                .phoneNumber("1111111111")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build());
        Contact primary2 = contactRepository.save(Contact.builder()
                .email("two@flux.com")
                .phoneNumber("2222222222")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build());
        // Trigger merge
        String json = """
            { "email": "one@flux.com", "phoneNumber": "2222222222" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        // The oldest primary should remain primary
        Contact expectedPrimary = primary1.getCreatedAt().isBefore(primary2.getCreatedAt()) ? primary1 : primary2;
        Long expectedPrimaryId = expectedPrimary.getId();
        // Fetch all related contacts after merge
        var allRelated = contactRepository.findAllByLinkedIdOrId(expectedPrimaryId);
        // All but the oldest should be secondary and linked to the oldest
        for (Contact c : allRelated) {
            if (!c.getId().equals(expectedPrimaryId)) {
                Assertions.assertEquals(LinkPrecedence.SECONDARY, c.getLinkPrecedence());
                Assertions.assertEquals(expectedPrimaryId.toString(), c.getLinkedId());
            } else {
                Assertions.assertEquals(LinkPrecedence.PRIMARY, c.getLinkPrecedence());
                Assertions.assertNull(c.getLinkedId());
            }
        }
        // Assert response
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        Assertions.assertEquals(expectedPrimaryId.intValue(), primaryContactId);
        java.util.List<String> emails = com.jayway.jsonpath.JsonPath.read(response, "$.contact.emails");
        Assertions.assertTrue(emails.contains("one@flux.com"));
        Assertions.assertTrue(emails.contains("two@flux.com"));
        java.util.List<String> phoneNumbers = com.jayway.jsonpath.JsonPath.read(response, "$.contact.phoneNumbers");
        Assertions.assertTrue(phoneNumbers.contains("1111111111"));
        Assertions.assertTrue(phoneNumbers.contains("2222222222"));
        java.util.List<Integer> secondaryIds = com.jayway.jsonpath.JsonPath.read(response, "$.contact.secondaryContactIds");
        java.util.List<Integer> expectedSecondaries = allRelated.stream()
                .filter(c -> !c.getId().equals(expectedPrimaryId))
                .map(Contact::getId)
                .map(Long::intValue)
                .collect(java.util.stream.Collectors.toList());
        Assertions.assertEquals(expectedSecondaries.size(), secondaryIds.size());
        Assertions.assertTrue(secondaryIds.containsAll(expectedSecondaries));
    }

    @Test
    void testNullValues() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": null, "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone(null, "9999999999");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
    }

    @Test
    void testRepeatedSecondaryLink() throws Exception {
        Contact primary = contactRepository.save(Contact.builder()
                .email("doc@future.com")
                .phoneNumber("9999999999")
                .linkPrecedence(LinkPrecedence.PRIMARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        Contact secondary = contactRepository.save(Contact.builder()
                .email("emmett@future.com")
                .phoneNumber("9999999999")
                .linkedId(primary.getId().toString())
                .linkPrecedence(LinkPrecedence.SECONDARY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        String json = """
            { "email": "emmett@future.com", "phoneNumber": "9999999999" }
        """;
        var result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andReturn();
        String response = result.getResponse().getContentAsString();
        var allContacts = findContactsByEmailOrPhone("emmett@future.com", "9999999999");
        Contact expectedPrimary = allContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.PRIMARY)
                .min(java.util.Comparator.comparing(Contact::getCreatedAt))
                .orElse(allContacts.get(0));
        int primaryContactId = com.jayway.jsonpath.JsonPath.read(response, "$.contact.primaryContactId");
        org.junit.jupiter.api.Assertions.assertEquals(expectedPrimary.getId().intValue(), primaryContactId);
    }
} 