package main.java.com.bitespeed.identityreconciliation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bitespeed.identityreconciliation.model.Contact;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByEmail(String email);
    List<Contact> findByPhoneNumber(String phoneNumber);
    List<Contact> findByEmailAndPhoneNumber(String email, String phoneNumber);
}





