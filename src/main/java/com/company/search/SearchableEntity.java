package com.company.search;

import java.util.Objects;

public class SearchableEntity {
    private final String id;
    private final String name;
    private final String type;
    
    public SearchableEntity(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SearchableEntity that = (SearchableEntity) obj;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(type, that.type);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, type);
    }
    
    @Override
    public String toString() {
        return String.format("SearchableEntity{id='%s', name='%s', type='%s'}", id, name, type);
    }
}