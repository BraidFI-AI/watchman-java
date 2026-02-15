package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that specific OFAC entities mentioned in BSA observations
 * are actually ingested from OFAC data.
 * 
 * BSA Consultant Retest Comments indicate the following entities are not being returned:
 * Row 6: CORPORACION CIMEX S.A., FINANCIERA CIMEX S.A
 * Row 21: AL-QAIDA GROUP OF JIHAD IN IRAQ, AL-QAIDA IN SYRIA
 * Row 22: TEHREEK-E-TALIBAN, TEHRIK-E TALIBAN PAKISTAN (TTP)
 * Row 35: CYBER CRIME OFFICE, ALSARAF HAWALA OFFICE, CENTRAL PUBLIC PROSECUTORS OFFICE
 * Row 52: OTKRITIE LTD GROUP, OTKRITIE BANK
 */
@SpringBootTest
@DisplayName("Entity Data Ingestion Verification")
class EntityDataIngestionTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    @DisplayName("Row 6: Verify CIMEX-related entities are ingested")
    void verifyCimexEntitiesIngested() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Find all CIMEX-related entities
        List<Entity> cimexEntities = allEntities.stream()
            .filter(e -> e.name().toUpperCase().contains("CIMEX"))
            .collect(Collectors.toList());
        
        System.out.println("\n=== CIMEX Entities Found (" + cimexEntities.size() + ") ===");
        cimexEntities.forEach(e -> {
            System.out.println("ID: " + e.sourceId() + " | Name: " + e.name());
            if (!e.altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", e.altNames()));
            }
        });
        
        // Check for specific entities mentioned in BSA observation
        boolean hasCorpCimex = cimexEntities.stream()
            .anyMatch(e -> e.name().toUpperCase().contains("CORPORACION CIMEX"));
        boolean hasFinancieraCimex = cimexEntities.stream()
            .anyMatch(e -> e.name().toUpperCase().contains("FINANCIERA CIMEX"));
        
        System.out.println("\n=== Missing Entities Check ===");
        System.out.println("CORPORACION CIMEX found: " + hasCorpCimex);
        System.out.println("FINANCIERA CIMEX found: " + hasFinancieraCimex);
        
        if (!hasCorpCimex || !hasFinancieraCimex) {
            System.out.println("\n⚠️  Some CIMEX entities are MISSING from ingested data");
        }
    }

    @Test
    @DisplayName("Row 21: Verify AL-QAIDA-related entities are ingested")
    void verifyAlQaidaEntitiesIngested() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Find all AL-QAIDA-related entities and aliases
        List<Entity> alQaidaEntities = allEntities.stream()
            .filter(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("AL-QAIDA") || name.contains("AL QAIDA") ||
                       name.contains("AL-QA'IDA") || name.contains("AL QA'IDA") ||
                       aliases.contains("AL-QAIDA") || aliases.contains("AL QAIDA");
            })
            .collect(Collectors.toList());
        
        System.out.println("\n=== AL-QAIDA Entities Found (" + alQaidaEntities.size() + ") ===");
        alQaidaEntities.forEach(e -> {
            System.out.println("ID: " + e.sourceId() + " | Name: " + e.name());
            if (!e.altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", e.altNames().stream().limit(5).collect(Collectors.toList())));
            }
        });
        
        // Check for specific entities/aliases mentioned in BSA observation
        boolean hasJihadInIraq = alQaidaEntities.stream()
            .anyMatch(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("JIHAD IN IRAQ") || aliases.contains("JIHAD IN IRAQ");
            });
        boolean hasInSyria = alQaidaEntities.stream()
            .anyMatch(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("IN SYRIA") || aliases.contains("IN SYRIA");
            });
        
        System.out.println("\n=== Missing Entities Check ===");
        System.out.println("AL-QAIDA GROUP OF JIHAD IN IRAQ found: " + hasJihadInIraq);
        System.out.println("AL-QAIDA IN SYRIA found: " + hasInSyria);
        
        if (!hasJihadInIraq || !hasInSyria) {
            System.out.println("\n⚠️  Some AL-QAIDA entities are MISSING from ingested data");
        }
    }

    @Test
    @DisplayName("Row 22: Verify TALIBAN-related entities are ingested")
    void verifyTalibanEntitiesIngested() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Find all TALIBAN-related entities and aliases
        List<Entity> talibanEntities = allEntities.stream()
            .filter(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("TALIBAN") || aliases.contains("TALIBAN");
            })
            .collect(Collectors.toList());
        
        System.out.println("\n=== TALIBAN Entities Found (" + talibanEntities.size() + ") ===");
        talibanEntities.forEach(e -> {
            System.out.println("ID: " + e.sourceId() + " | Name: " + e.name());
            if (!e.altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", e.altNames().stream().limit(5).collect(Collectors.toList())));
            }
        });
        
        // Check for specific entities mentioned in BSA observation
        boolean hasTehreek = talibanEntities.stream()
            .anyMatch(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("TEHREEK-E-TALIBAN") || name.contains("TEHRIK-E TALIBAN") ||
                       aliases.contains("TEHREEK-E-TALIBAN") || aliases.contains("TEHRIK-E TALIBAN");
            });
        
        System.out.println("\n=== Missing Entities Check ===");
        System.out.println("TEHREEK-E-TALIBAN / TEHRIK-E TALIBAN PAKISTAN found: " + hasTehreek);
        
        if (!hasTehreek) {
            System.out.println("\n⚠️  TEHREEK-E-TALIBAN entity is MISSING from ingested data");
        }
    }

    @Test
    @DisplayName("Row 35: Verify OFFICE-related entities are ingested")
    void verifyOfficeEntitiesIngested() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Find all OFFICE-related entities
        List<Entity> officeEntities = allEntities.stream()
            .filter(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return (name.contains("OFFICE") || aliases.contains("OFFICE")) &&
                       (name.contains("39") || aliases.contains("39") ||
                        name.contains("CYBER") || aliases.contains("CYBER") ||
                        name.contains("ALSARAF") || aliases.contains("ALSARAF"));
            })
            .collect(Collectors.toList());
        
        System.out.println("\n=== OFFICE Entities Found (" + officeEntities.size() + ") ===");
        officeEntities.forEach(e -> {
            System.out.println("ID: " + e.sourceId() + " | Name: " + e.name());
            if (!e.altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", e.altNames().stream().limit(5).collect(Collectors.toList())));
            }
        });
    }

    @Test
    @DisplayName("Row 52: Verify OTKRITIE-related entities are ingested")
    void verifyOtkritieEntitiesIngested() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Find all OTKRITIE-related entities
        List<Entity> otkritieEntities = allEntities.stream()
            .filter(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("OTKRITIE") || aliases.contains("OTKRITIE");
            })
            .collect(Collectors.toList());
        
        System.out.println("\n=== OTKRITIE Entities Found (" + otkritieEntities.size() + ") ===");
        otkritieEntities.forEach(e -> {
            System.out.println("ID: " + e.sourceId() + " | Name: " + e.name());
            if (!e.altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", e.altNames().stream().limit(5).collect(Collectors.toList())));
            }
        });
        
        // Check for specific entities mentioned in BSA observation
        boolean hasOtkritieBank = otkritieEntities.stream()
            .anyMatch(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("OTKRITIE BANK") || name.contains("OTKRITIE LTD") ||
                       aliases.contains("OTKRITIE BANK") || aliases.contains("OTKRITIE LTD");
            });
        
        System.out.println("\n=== Missing Entities Check ===");
        System.out.println("OTKRITIE BANK / OTKRITIE LTD GROUP found: " + hasOtkritieBank);
        
        if (!hasOtkritieBank) {
            System.out.println("\n⚠️  Some OTKRITIE entities are MISSING from ingested data");
        }
    }
}
