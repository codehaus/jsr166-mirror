package jsr166.test;

import java.util.*;


public final class Sample {
    public static void main(String[] args) {
        //Pair.main(args);
    }
    
    
    /**
     * Assuming a file named Localized.properties with these contents:
     *
     * <pre>
     *   Color.RED = flaming
     *   Color.GREEN = bilious
     *   Color.BLUE = cerulean
     * </pre>
     * 
     * in the same package directory as this class, then this program prints
     *
     * <pre>
     *   Local name for BLUE is cerulean
     * </pre>
     */
    void testLocalized() {
        Localized<Color> color = Localized.valueOf(Color.BLUE);
        System.out.println("Local name for "+color.enumValue()+" is "+color.localizedName());
    }
}


class Pair<K, V> implements Map.Entry<K, V>, Iterable<Pair<K, V>>, Iterator<Pair<K, V>> {
    
    public static <K, V> Iterable<Pair<K, V>> iterable(Collection<K> keys, Collection<V> values) {
        return new Pair<K, V>(keys, values);
    }
    
    public static <K, V> Iterable<Pair<K, V>> iterable(K[] keys, Collection<V> values) {
        return new Pair<K, V>(Arrays.asList(keys), values);
    }
    
    public static <K, V> Iterable<Pair<K, V>> iterable(Collection<K> keys, V[] values) {
        return new Pair<K, V>(keys, Arrays.asList(values));
    }
    
    public static <K, V> Iterable<Pair<K, V>> iterable(K[] keys, V[] values) {
        return new Pair<K, V>(Arrays.asList(keys), Arrays.asList(values));
    }
    
    // Map.Entry methods
    
    public K getKey() { return key; }
    public V getValue() { return value; }
    public V setValue(V value) {
        throw new UnsupportedOperationException("unmodifiable");
    }
    
    private K key;
    private V value;
    
    
    // Iterable methods
                
    public Iterator<Pair<K, V>> iterator() {
        return this;
    }


    // Iterator methods
    
    public boolean hasNext() {
        return keys.hasNext() && values.hasNext();
    }

    public Pair<K, V> next() {
        key = keys.next();
        value = values.next();
        return this;
    }
    
    public void remove() {
        throw new UnsupportedOperationException("unmodifiable");
    }
    
    private Pair(Collection<K> keys, Collection<V> values) {
        if (keys.size() != values.size()) 
            throw new IllegalArgumentException("sizes must match");
        this.keys = keys.iterator();
        this.values = values.iterator();
    }
    
    private final Iterator<K> keys;
    private final Iterator<V> values;

    /** Sample usage */
    public static void main(String[] args) {
        String [] words  = new String [] { "one", "two", "three" };
        Integer[] values = new Integer[] {   1  ,   2  ,    3    };
        
        for (Pair<String, Integer> pair : Pair.iterable(words, values)) {
            System.out.println("Word "+pair.getKey()+" has value "+pair.getValue());
        }
    }
}


class Localized<E extends Enum<E>> {

    public E enumValue() { return value; }

    public String localizedName() {
        return bundle.getString(propertyKey());
    }

    public String propertyKey() {
        return className+"."+value.toString();
    }

    public static <T extends Enum<T>> Localized<T> valueOf(T value) {
        if (!cache.containsKey(value))
            cache.put(value, new Localized<T>(value));
        return (Localized<T>) cache.get(value);
    }

    private Localized(E value) { 
        this.value = value; 
        this.className = value.getClass().getName().replaceFirst(".*\\.", "");
    }

    private final E value;
    private final String className;

    private static final Map<Enum, Localized<?>> cache = 
        new IdentityHashMap<Enum, Localized<?>>();

    private static final ResourceBundle bundle = 
        ResourceBundle.getBundle(Localized.class.getPackage().getName()+".Localized");
}


enum Color { RED, GREEN, BLUE, }
