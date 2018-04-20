package common;

/**
 * Immutable pair class for storing simple related values
 *
 * @param <K> Key data type
 * @param <V> Value data type
 */
public class Pair<K, V> {
    private final K key;
    private final V val;

    /**
     * Class entry
     *
     * @param key Key data
     * @param val Value data
     */
    public Pair(K key, V val) {
        this.key = key;
        this.val = val;
    }

    /**
     * Gets the key
     *
     * @return key
     */
    public K getKey() {
        return key;
    }

    /**
     * Gets the value
     *
     * @return value
     */
    public V getVal() {
        return val;
    }
}
