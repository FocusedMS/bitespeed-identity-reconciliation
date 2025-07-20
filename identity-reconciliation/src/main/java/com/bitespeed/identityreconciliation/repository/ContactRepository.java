package com.bitespeed.identityreconciliation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bitespeed.identityreconciliation.model.Contact;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Find contacts that exactly match the email or phone from the request
    @Query("SELECT c FROM Contact c WHERE (:email IS NOT NULL AND c.email = :email) OR (:phone IS NOT NULL AND c.phoneNumber = :phone)")
    List<Contact> findByEmailOrPhoneNumber(@Param("email") String email, @Param("phone") String phone);

    // Find all contacts linked to a primary contact
    @Query("SELECT c FROM Contact c WHERE (c.linkedId IS NOT NULL AND CAST(c.linkedId AS long) = :id) OR c.id = :id")
    List<Contact> findAllByLinkedIdOrId(@Param("id") Long id);

    // Find contacts by exact email match
    @Query("SELECT c FROM Contact c WHERE c.email = :email")
    List<Contact> findByEmail(@Param("email") String email);

    // Find contacts by exact phone match
    @Query("SELECT c FROM Contact c WHERE c.phoneNumber = :phone")
    List<Contact> findByPhoneNumber(@Param("phone") String phone);
}



