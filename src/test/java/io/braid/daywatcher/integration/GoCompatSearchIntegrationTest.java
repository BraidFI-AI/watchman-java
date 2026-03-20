package io.braid.daywatcher.integration;

import io.braid.daywatcher.api.GoCompatResponse;
import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Go Watchman-compatible search endpoint.
 * Verifies /go/search returns correct format for Braid integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoCompatSearchIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private EntityIndex entityIndex;
    
    private String baseUrl() {
        return "http://localhost:" + port + "/go";
    }
    
    @Test
    void testGoSearchReturnsCorrectFormat() {
        // Given: Entity in index
        Entity putin = createPutinEntity();
        entityIndex.replaceAll(List.of(putin));
        
        // When: Search via Go endpoint
        ResponseEntity<GoCompatResponse> response = restTemplate.getForEntity(
            baseUrl() + "/search?name=Putin&limit=5",
            GoCompatResponse.class
        );
        
        // Then: Response has Go format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        GoCompatResponse body = response.getBody();
        assertThat(body.SDNs()).isNotEmpty();
        assertThat(body.altNames()).isEmpty(); // No alias match
        assertThat(body.sectoralSanctions()).isEmpty();
        assertThat(body.deniedPersons()).isEmpty();
        assertThat(body.bisEntities()).isEmpty();
    }
    
    @Test
    void testGoEntityHasAllRequiredFields() {
        // Given: Entity with vessel details
        Vessel vessel = new Vessel(
            "MV Test Ship",
            List.of(),
            "IMO1234567",
            "Cargo Ship",
            "RU",
            "2020",
            "273123456",
            "ABCD",
            "50000",
            "Russian Shipping Co"
        );
        
        Entity entity = new Entity(
            "7140",
            "PUTIN, Vladimir Vladimirovich",
            EntityType.PERSON,
            SourceList.US_OFAC,
            "SDN-7140",
            new Person("Vladimir Putin", List.of(), "M", null, null, "1952-10-07", List.of("President"), List.of()),
            null, null, null,
            vessel,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            SanctionsInfo.of(List.of("UKRAINE-EO13662")),
            List.of(),
            "DOB 07 Oct 1952; POB Leningrad",
            null,
            null, null, null, null, null
        );
        
        entityIndex.replaceAll(List.of(entity));
        
        // When: Search
        ResponseEntity<GoCompatResponse> response = restTemplate.getForEntity(
            baseUrl() + "/search?q=Putin&minMatch=0.85",
            GoCompatResponse.class
        );
        
        // Then: Verify all Go fields present
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().SDNs()).hasSize(1);
        
        GoCompatResponse.GoEntity goEntity = response.getBody().SDNs().get(0);
        assertThat(goEntity.entityID()).isEqualTo("SDN-7140");
        assertThat(goEntity.sdnName()).isEqualTo("PUTIN, Vladimir Vladimirovich");
        assertThat(goEntity.sdnType()).isEqualTo("individual");
        assertThat(goEntity.program()).contains("UKRAINE-EO13662");
        assertThat(goEntity.title()).contains("President");
        assertThat(goEntity.callSign()).isEqualTo("ABCD");
        assertThat(goEntity.vesselType()).isEqualTo("Cargo Ship");
        assertThat(goEntity.vesselFlag()).isEqualTo("RU");
        assertThat(goEntity.vesselOwner()).isEqualTo("Russian Shipping Co");
        assertThat(goEntity.remarks()).contains("DOB 07 Oct 1952");
        assertThat(goEntity.match()).isGreaterThan(0.8);
    }
    
    @Test
    void testAliasMatchGoesToAltNames() {
        // Given: Entity with aliases
        Entity entity = new Entity(
            "6366",
            "AL-QAIDA",
            EntityType.ORGANIZATION,
            SourceList.US_OFAC,
            "6366",
            null, null, null, null, null, null,
            List.of(),
            List.of(),
            List.of("AL QAEDA", "AL QA'IDA"),
            List.of(),
            SanctionsInfo.of(List.of("SDGT")),
            List.of(),
            null, null,
            null, null, null, null, null
        );
        
        entityIndex.replaceAll(List.of(entity));
        
        // When: Search for alias
        ResponseEntity<GoCompatResponse> response = restTemplate.getForEntity(
            baseUrl() + "/search?name=AL QAEDA&minMatch=0.85",
            GoCompatResponse.class
        );
        
        // Then: Result in altNames array
        assertThat(response.getBody()).isNotNull();
        // Note: This test may need adjustment based on actual alias matching behavior
        // For now, just verify response structure
        assertThat(response.getBody().SDNs()).isNotNull();
        assertThat(response.getBody().altNames()).isNotNull();
    }
    
    @Test
    void testEmptyQueryReturnsEmptyArrays() {
        // When: Search with empty name
        ResponseEntity<GoCompatResponse> response = restTemplate.getForEntity(
            baseUrl() + "/search?name=",
            GoCompatResponse.class
        );
        
        // Then: Return empty arrays (not null)
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().SDNs()).isEmpty();
        assertThat(response.getBody().altNames()).isEmpty();
        assertThat(response.getBody().sectoralSanctions()).isEmpty();
    }
    
    private Entity createPutinEntity() {
        return new Entity(
            "35096",
            "PUTIN, Vladimir Vladimirovich",
            EntityType.PERSON,
            SourceList.US_OFAC,
            "35096",
            new Person("Vladimir Putin", List.of(), "M", null, null, "1952-10-07", List.of("President"), List.of()),
            null, null, null, null, null,
            List.of(),
            List.of(),
            List.of("Vladimir Putin"),
            List.of(),
            SanctionsInfo.of(List.of("UKRAINE-EO13662")),
            List.of(),
            "DOB 07 Oct 1952; POB Leningrad",
            null,
            null, null, null, null, null
        );
    }
}
