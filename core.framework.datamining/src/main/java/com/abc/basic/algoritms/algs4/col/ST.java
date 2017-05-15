package com.abc.basic.algoritms.algs4.col;

import com.abc.basic.algoritms.algs4.utils.StdIn;
import com.abc.basic.algoritms.algs4.utils.StdOut;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * 符号表
 * @param <Key>
 * @param <Value>
 */
public class ST<Key extends Comparable<Key>, Value> implements Iterable<Key> {

    private TreeMap<Key, Value> st;

    public ST() {
        st = new TreeMap<Key, Value>();
    }

    public TreeMap<Key, Value> getST(){
        return st;
    }
    public Value get(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("called get() with null key");
        }
        return st.get(key);
    }

    public void put(Key key, Value val) {
        if (key == null) {
            throw new IllegalArgumentException("called put() with null key");
        }
        if (val == null) {
            st.remove(key);
        }else {
            st.put(key, val);
        }
    }

    public void delete(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("called delete() with null key");
        }
        st.remove(key);
    }

    public boolean contains(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("called contains() with null key");
        }
        return st.containsKey(key);
    }

    public int size() {
        return st.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }


    public Iterable<Key> keys() {
        return st.keySet();
    }

    @Deprecated
    public Iterator<Key> iterator() {
        return st.keySet().iterator();
    }

    public Key min() {
        if (isEmpty()) {
            throw new NoSuchElementException("called min() with empty symbol table");
        }
        return st.firstKey();
    }

    public Key max() {
        if (isEmpty()) {
            throw new NoSuchElementException("called max() with empty symbol table");
        }
        return st.lastKey();
    }

    public Key ceiling(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("called ceiling() with null key");
        }
        Key k = st.ceilingKey(key);
        if (k == null) {
            throw new NoSuchElementException("all keys are less than " + key);
        }
        return k;
    }

    public Key floor(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("called floor() with null key");
        }
        Key k = st.floorKey(key);
        if (k == null) {
            throw new NoSuchElementException("all keys are greater than " + key);
        }
        return k;
    }

    public static void main(String[] args) {
        ST<String, Integer> st = new ST<String, Integer>();
        for (int i = 0; !StdIn.isEmpty(); i++) {
            String key = StdIn.readString();
            st.put(key, i);
        }
        for (String s : st.keys())
            StdOut.println(s + " " + st.get(s));
    }
}
