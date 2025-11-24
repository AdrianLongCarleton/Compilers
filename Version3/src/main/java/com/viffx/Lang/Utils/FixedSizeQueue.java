package com.viffx.Lang.Utils;

import java.util.ArrayDeque;
import java.util.Deque;

public class FixedSizeQueue<E> {
    private final int maxSize;
    private final Deque<E> deque;

    public FixedSizeQueue(int maxSize) {
        this.maxSize = maxSize;
        this.deque = new ArrayDeque<>(maxSize);
    }

    public void add(E e) {
        if (deque.size() == maxSize) {
            deque.removeFirst(); // drop oldest
        }
        deque.addLast(e);
    }

    public E get(int index) {
        if (index < 0 || index >= deque.size()) {
            throw new IndexOutOfBoundsException();
        }
        return deque.stream().skip(index).findFirst().get();
    }

    public int size() {
        return deque.size();
    }

    @Override
    public String toString() {
        return deque.toString();
    }
}
