import io.moov.watchman.model.*;
import io.moov.watchman.similarity.*;
import java.util.*;

public class TestStopwordDebug {
    public static void main(String[] args) {
        Person person = new Person(
            "PER123",
            List.of("THE KINGPIN", "EL JEFE"),
            "male",
            null, null, null,
            List.of(),
            List.of()
        );
        
        Entity entity = new Entity(
            "test-id",
            "THE DRUG LORD",
            EntityType.PERSON,
            SourceList.US_OFAC,
            "test-id",
            person, null, null, null, null,
            null,
            List.of(), List.of(), List.of(), List.of(),
            null, List.of(), null, null
        );
        
        Entity normalized = entity.normalize();
        PreparedFields prepared = normalized.preparedFields();
        
        System.out.println("Primary name: '" + prepared.normalizedPrimaryName() + "'");
        System.out.println("Contains 'the': " + prepared.normalizedPrimaryName().contains("the"));
        System.out.println("Alt names: " + prepared.normalizedAltNames());
    }
}
