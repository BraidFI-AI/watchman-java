package io.moov.watchman.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NemesisTriggerRequestTest {

    @Test
    void shouldCreateRequestWithDefaultValues() {
        // Given/When: using builder with defaults
        NemesisController.TriggerRequest request = new NemesisController.TriggerRequest(
            100,         // queries
            false,       // includeOfacApi  
            true,        // async
            true,        // javaEnabled (new - default true)
            false,       // goEnabled (new - default false)
            false        // braidEnabled (new - default false)
        );

        // Then
        assertEquals(100, request.queries());
        assertFalse(request.includeOfacApi());
        assertTrue(request.async());
        assertTrue(request.javaEnabled());
        assertFalse(request.goEnabled());
        assertFalse(request.braidEnabled());
    }

    @Test
    void shouldValidateAtLeastOneTargetEnabled() {
        // When/Then: all targets disabled should fail
        assertThrows(IllegalArgumentException.class, 
            () -> new NemesisController.TriggerRequest(
                100, false, true,
                false,  // javaEnabled
                false,  // goEnabled  
                false   // braidEnabled - all disabled!
            ));
    }

    @Test
    void shouldAllowJavaOnly() {
        // When
        NemesisController.TriggerRequest request = new NemesisController.TriggerRequest(
            100, false, true,
            true,   // javaEnabled
            false,  // goEnabled
            false   // braidEnabled
        );

        // Then
        assertTrue(request.javaEnabled());
        assertFalse(request.goEnabled());
        assertFalse(request.braidEnabled());
    }

    @Test
    void shouldAllowBraidPlusJava() {
        // When
        NemesisController.TriggerRequest request = new NemesisController.TriggerRequest(
            100, false, true,
            true,   // javaEnabled
            false,  // goEnabled
            true    // braidEnabled
        );

        // Then
        assertTrue(request.javaEnabled());
        assertFalse(request.goEnabled());
        assertTrue(request.braidEnabled());
    }

    @Test
    void shouldAllowAllThreeTargets() {
        // When
        NemesisController.TriggerRequest request = new NemesisController.TriggerRequest(
            100, false, true,
            true,  // javaEnabled
            true,  // goEnabled
            true   // braidEnabled - 3-way comparison
        );

        // Then
        assertTrue(request.javaEnabled());
        assertTrue(request.goEnabled());
        assertTrue(request.braidEnabled());
    }

    @Test
    void shouldValidateQueryCount() {
        // When/Then: queries <= 0
        assertThrows(IllegalArgumentException.class,
            () -> new NemesisController.TriggerRequest(0, false, true, true, false, false));

        // When/Then: queries > 1000
        assertThrows(IllegalArgumentException.class,
            () -> new NemesisController.TriggerRequest(1001, false, true, true, false, false));
    }
}
