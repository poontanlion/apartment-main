package com.victory.apartment.integration;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.repository.MaintenancePredictionRepository;
import com.victory.apartment.repository.MaintenanceTaskTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MaintenancePredictionSchemaTest {
    @Autowired MaintenanceTaskTypeRepository types;
    @Autowired MaintenancePredictionRepository predictions;

    @Test void bootsWithHibernateValidationAndNewRepositories() {
        assertEquals(5, types.count());
        assertEquals("Air-con servicing", types.findByCode("AIRCON").orElseThrow().getCategoryKey());
        assertFalse(types.findByCode("GENERAL").orElseThrow().getIsRecurring());
        assertTrue(predictions.findByRoomIdAndTaskTypeId("missing", "mtt-aircon").isEmpty());
    }
}
