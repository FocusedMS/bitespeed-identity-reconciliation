package com.bitespeed.identityreconciliation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bitespeed.identityreconciliation.model.Contact;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Query("SELECT c FROM Contact c WHERE (:email IS NULL OR c.email = :email) OR (:phone IS NULL OR c.phoneNumber = :phone)")
    List<Contact> findByEmailOrPhoneNumber(@Param("email") String email, @Param("phone") String phone);

    @Query("SELECT c FROM Contact c WHERE (c.linkedId IS NOT NULL AND CAST(c.linkedId AS long) = :id) OR c.id = :id")
    List<Contact> findAllByLinkedIdOrId(@Param("id") Long id);
}



