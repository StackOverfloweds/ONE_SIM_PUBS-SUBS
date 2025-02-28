package routing.util;

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
    public void setSecond(B second) { this.second = second; }

    public A getFirst() {
        return first;
    }

    @Override
    public String toString() {
        return "Tuple [first=" + first + ", second=" + second + "]";
    }

    public B getSecond() {
        return second;
    }

    public boolean isEmpty() {
        return first == null && second == null;
    }
}
