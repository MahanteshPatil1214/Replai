package com.replai.tickets;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for tests that boot the real JPA mappings against in-memory H2,
 * so every repository query is parsed and executed instead of mocked away.
 *
 * H2 runs in PostgreSQL mode; `jsonb` is declared as a domain because the
 * tickets.sources column uses a Postgres columnDefinition.
 */
@DataJpaTest
// Use the H2 URL above (with the jsonb domain) instead of the auto-generated one.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:replai;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS jsonb AS JSON",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
public abstract class RepositoryTest {
}
