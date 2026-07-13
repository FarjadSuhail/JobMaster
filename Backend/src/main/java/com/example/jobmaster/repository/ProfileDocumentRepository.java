package com.example.jobmaster.repository;

import com.example.jobmaster.domain.ProfileDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileDocumentRepository extends JpaRepository<ProfileDocument, Long> {
}
