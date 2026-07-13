package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정된 runtime/migration endpoint의 PostgreSQL·pgvector 연결만 확인한다.
 * 실제 운영 OpenSQL/OpenProxy/OpenHA 환경의 장애 복구까지 검증한 테스트는 아니다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_OPENSQL_TESTS", matches = "(?i:true|1)")
@ActiveProfiles("opensql")
@SpringBootTest
class OpenSqlInfrastructureTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void providesPostgres16AndPgVectorThroughConfiguredRuntimeEndpoint() {
        Integer serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer", Integer.class);

        PgVectorSmokeAssertions.SmokeResult result =
                PgVectorSmokeAssertions.verifyExactCosineSearch(jdbcTemplate);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(result.extensionVersion()).isNotBlank();
    }
}
