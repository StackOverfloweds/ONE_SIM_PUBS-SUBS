package routing.util;

import java.util.List;

public class TupleDe<A, B> {
    private A first;
    private B second;

    public TupleDe(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public void setFirst(A first) {
        this.first = first;
    }

    public void setSecond(B second) {
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public boolean isEmpty() {
        return first == null && second == null;
    }

    @Override
    public String toString() {
        return "Tuple [first=" + first + ", second=" + second + "]";
    }

    /** 🔹 Calculate estimated size of the tuple in bytes */
    public int getSize() {
        int size = 8; // Base object overhead

        size += getObjectSize(first);
        size += getObjectSize(second);

        return size;
    }

    /**
     * 🔹 Returns the number of non-null elements in the tuple
     */
    public int size() {
        int count = 0;
        if (first != null) count++;
        if (second != null) count++;
        return count;
    }


    /** 🔹 Helper method to determine object size */
    private int getObjectSize(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Boolean) return 1;
        if (obj instanceof Integer) return 4;
        if (obj instanceof Long) return 8;
        if (obj instanceof Double) return 8;
        if (obj instanceof Float) return 4;
        if (obj instanceof Short) return 2;
        if (obj instanceof Byte) return 1;
        if (obj instanceof Character) return 2;
        if (obj instanceof String) return ((String) obj).length() * 2; // String uses 2 bytes per char
        if (obj instanceof List<?>) return ((List<?>) obj).size() * 4; // Estimate list size

        return 16; // Default estimation for unknown objects
    }
}
