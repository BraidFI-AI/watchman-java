package io.moov.watchman.nemesis;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a static test scenario for Nemesis comparison testing.
 * Scenarios are loaded from YAML and define known test cases for validating
 * Java Watchman against Braid (Go), direct Go API, or OFAC-API.
 */
public class TestScenario {
    private final String id;
    private final String name;
    private final String type;
    private final List<String> altNames;
    private final List<String> addresses;
    private final String category;
    private final Boolean expectedMatch;
    private final String notes;

    private TestScenario(Builder builder) {
        if (builder.id == null || builder.id.isBlank()) {
            throw new IllegalArgumentException("Scenario id is required");
        }
        if (builder.name == null || builder.name.isBlank()) {
            throw new IllegalArgumentException("Scenario name is required");
        }
        if (builder.type == null || builder.type.isBlank()) {
            throw new IllegalArgumentException("Scenario type is required");
        }

        this.id = builder.id;
        this.name = builder.name;
        this.type = builder.type;
        this.altNames = builder.altNames != null ? new ArrayList<>(builder.altNames) : new ArrayList<>();
        this.addresses = builder.addresses != null ? new ArrayList<>(builder.addresses) : new ArrayList<>();
        this.category = builder.category;
        this.expectedMatch = builder.expectedMatch;
        this.notes = builder.notes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public List<String> getAltNames() {
        return new ArrayList<>(altNames);
    }

    public List<String> getAddresses() {
        return new ArrayList<>(addresses);
    }

    public String getCategory() {
        return category;
    }

    public Boolean isExpectedMatch() {
        return expectedMatch;
    }

    public String getNotes() {
        return notes;
    }

    public static class Builder {
        private String id;
        private String name;
        private String type;
        private List<String> altNames;
        private List<String> addresses;
        private String category;
        private Boolean expectedMatch;
        private String notes;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder altNames(List<String> altNames) {
            this.altNames = altNames;
            return this;
        }

        public Builder addresses(List<String> addresses) {
            this.addresses = addresses;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder expectedMatch(Boolean expectedMatch) {
            this.expectedMatch = expectedMatch;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public TestScenario build() {
            return new TestScenario(this);
        }
    }
}
