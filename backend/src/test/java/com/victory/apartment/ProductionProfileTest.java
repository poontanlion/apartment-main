
package com.victory.apartment;

import static org.assertj.core.api.Assertions.assertThat;

import com.victory.apartment.config.AdminBootstrap;
import com.victory.apartment.config.DataSeeder;
import com.victory.apartment.repository.AppUserRepository;
import com.victory.apartment.repository.RoomRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.admin.email=b5-prod-test@example.com",
        "app.admin.password=B5-Test-Password-2026!",
        "app.admin.username=b5-prod-admin"
})
class ProductionProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoomRepository rooms;

    @Test
    void productionDoesNotLoadDemoSeeder() {
        assertThat(context.getBeansOfType(DataSeeder.class))
                .isEmpty();

        assertThat(rooms.count()).isZero();
    }

    @Test
    void productionCreatesAdminWithoutDemoData() {
        assertThat(context.getBeansOfType(AdminBootstrap.class))
                .hasSize(1);

        assertThat(users.existsByEmail("b5-prod-test@example.com"))
                .isTrue();

        assertThat(rooms.count()).isZero();
    }

    @Test
    void runningAdminBootstrapAgainDoesNotDuplicateAdmin() {
        AdminBootstrap bootstrap =
                context.getBean(AdminBootstrap.class);

        long before = users.count();

        bootstrap.initializeAdmin();

        assertThat(users.count()).isEqualTo(before);
    }
}
