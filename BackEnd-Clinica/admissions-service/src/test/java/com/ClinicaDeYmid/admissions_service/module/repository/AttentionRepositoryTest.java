package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.entity.Location;
import com.ClinicaDeYmid.admissions_service.module.entity.ServiceType;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AttentionRepositoryTest {

    @Autowired
    private AttentionRepository attentionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByPatientId_Success() {
        // Arrange
        ServiceType serviceType = new ServiceType();
        serviceType.setName("Service Type Test");
        serviceType.setActive(true);
        serviceType.setCreatedAt(LocalDateTime.now());
        serviceType = entityManager.persist(serviceType);

        Location location = new Location();
        location.setName("Location Test");
        location.setActive(true);
        location.setCreatedAt(LocalDateTime.now());
        location = entityManager.persist(location);

        ConfigurationService configService = new ConfigurationService();
        configService.setServiceType(serviceType);
        configService.setLocation(location);
        configService.setActive(true);
        configService.setCreatedAt(LocalDateTime.now());
        configService = entityManager.persist(configService);

        Attention attention = new Attention();
        attention.setPatientId(100L);
        attention.setDoctorId(200L);
        attention.setStatus(AttentionStatus.CREATED);
        attention.setCause(Cause.ROUTINE_CHECKUP);
        attention.setConfigurationService(configService);
        attention.setCreatedAt(LocalDateTime.now());
        attention.setUpdatedAt(LocalDateTime.now());
        
        attentionRepository.save(attention);

        // Act
        List<Attention> result = attentionRepository.findByPatientId(100L);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(100L, result.get(0).getPatientId());
    }

    @Test
    void findByPatientIdAndStatus_Success() {
        // Arrange
        ServiceType serviceType = new ServiceType();
        serviceType.setName("Service Type Test 2");
        serviceType.setActive(true);
        serviceType.setCreatedAt(LocalDateTime.now());
        serviceType = entityManager.persist(serviceType);

        Location location = new Location();
        location.setName("Location Test 2");
        location.setActive(true);
        location.setCreatedAt(LocalDateTime.now());
        location = entityManager.persist(location);

        ConfigurationService configService = new ConfigurationService();
        configService.setServiceType(serviceType);
        configService.setLocation(location);
        configService.setActive(true);
        configService.setCreatedAt(LocalDateTime.now());
        configService = entityManager.persist(configService);

        Attention attention = new Attention();
        attention.setPatientId(101L);
        attention.setDoctorId(201L);
        attention.setStatus(AttentionStatus.CREATED);
        attention.setCause(Cause.ROUTINE_CHECKUP);
        attention.setConfigurationService(configService);
        attention.setCreatedAt(LocalDateTime.now());
        attention.setUpdatedAt(LocalDateTime.now());

        attentionRepository.save(attention);

        // Act
        Optional<Attention> result = attentionRepository.findByPatientIdAndStatus(101L, AttentionStatus.CREATED);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getPatientId());
    }
}