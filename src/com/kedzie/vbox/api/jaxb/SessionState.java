







package com.kedzie.vbox.api.jaxb;




 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
public enum SessionState {

        NULL("Null"),
        UNLOCKED("Unlocked"),
        LOCKED("Locked"),
        SPAWNING("Spawning"),
        UNLOCKING("Unlocking");
    private final String value;

    SessionState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }
    public String toString() { return value; }
    public static SessionState fromValue(String v) {
        for (SessionState c: SessionState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}