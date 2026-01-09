package io.moov.watchman.search;

/**
 * Tracks categorized field information for entity scoring.
 * Used to determine which critical fields were matched.
 */
public class EntityFields {
    private int required;
    private int available;
    private boolean hasName;
    private boolean hasID;
    private boolean hasCritical;
    private boolean hasAddress;

    public EntityFields() {
        this.required = 0;
        this.available = 0;
        this.hasName = false;
        this.hasID = false;
        this.hasCritical = false;
        this.hasAddress = false;
    }

    public int getRequired() {
        return required;
    }

    public void setRequired(int required) {
        this.required = required;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public boolean isHasName() {
        return hasName;
    }

    public void setHasName(boolean hasName) {
        this.hasName = hasName;
    }

    public boolean isHasID() {
        return hasID;
    }

    public void setHasID(boolean hasID) {
        this.hasID = hasID;
    }

    public boolean isHasCritical() {
        return hasCritical;
    }

    public void setHasCritical(boolean hasCritical) {
        this.hasCritical = hasCritical;
    }

    public boolean isHasAddress() {
        return hasAddress;
    }

    public void setHasAddress(boolean hasAddress) {
        this.hasAddress = hasAddress;
    }
}
