package org.opendaylight.affinity.affinity;

/**
 * The enumeration of affinity attributes. 
 */
public enum AffinityAttributeType {
    SET_DENY("deny"),
    SET_TAP("set_tap"),
    SET_PATH_ISOLATE("set_path_isolate"),
    SET_PATH_REDIRECT("set_path_redirect");

    String id;

    private AffinityAttributeType(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public int calculateConsistentHashCode() {
        if (this.id != null) {
            return this.id.hashCode();
        } else {
            return 0;
        }
    }

}