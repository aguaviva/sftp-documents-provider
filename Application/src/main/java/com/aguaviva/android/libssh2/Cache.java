package com.aguaviva.android.libssh2;

import java.util.concurrent.ConcurrentHashMap;

public class Cache<T> {
    ConcurrentHashMap<String, T> data;

    T get(String s) {
        return data.get(s);
    }

    T put(String s, T t) {
        return data.put(s, t);
    }

}
