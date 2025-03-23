package com.pixelservices.flash.components.http.routing.trie;

import com.pixelservices.flash.components.http.routing.models.RouteEntry;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

public class LiteralRouteTrie {
    private static final class CompactTrieNode {
        private static final int INITIAL_CAPACITY = 16;
        char[] childChars = new char[INITIAL_CAPACITY];
        CompactTrieNode[] childNodes = new CompactTrieNode[INITIAL_CAPACITY];
        int childCount = 0;
        RouteEntry routeEntry = null;

        CompactTrieNode findOrCreateChild(char c) {
            int index = Arrays.binarySearch(childChars, 0, childCount, c);
            if (index >= 0) {
                return childNodes[index];
            }

            if (childCount == childChars.length) {
                int newCapacity = childChars.length * 2;
                childChars = Arrays.copyOf(childChars, newCapacity);
                childNodes = Arrays.copyOf(childNodes, newCapacity);
            }

            index = -(index + 1);
            System.arraycopy(childChars, index, childChars, index + 1, childCount - index);
            System.arraycopy(childNodes, index, childNodes, index + 1, childCount - index);

            CompactTrieNode newChild = new CompactTrieNode();
            childChars[index] = c;
            childNodes[index] = newChild;
            childCount++;
            return newChild;
        }
    }

    private final CompactTrieNode root = new CompactTrieNode();
    private final StampedLock lock = new StampedLock();

    public void insert(String key, RouteEntry routeEntry) {
        long stamp = lock.writeLock();
        try {
            CompactTrieNode node = root;
            for (char c : key.toCharArray()) {
                node = node.findOrCreateChild(c);
            }
            node.routeEntry = routeEntry;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public RouteEntry search(String key) {
        long stamp = lock.tryOptimisticRead();

        CompactTrieNode node = root;
        for (char c : key.toCharArray()) {
            int index = Arrays.binarySearch(node.childChars, 0, node.childCount, c);
            if (index < 0) return null;
            node = node.childNodes[index];
        }

        return lock.validate(stamp) ? node.routeEntry : searchWithReadLock(key);
    }

    private RouteEntry searchWithReadLock(String key) {
        long stamp = lock.readLock();
        try {
            CompactTrieNode node = root;
            for (char c : key.toCharArray()) {
                int index = Arrays.binarySearch(node.childChars, 0, node.childCount, c);
                if (index < 0) return null;
                node = node.childNodes[index];
            }
            return node.routeEntry;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int count = countRoutes(root);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                return countRoutes(root);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return count;
    }

    private int countRoutes(CompactTrieNode node) {
        int cnt = (node.routeEntry != null) ? 1 : 0;
        for (int i = 0; i < node.childCount; i++) {
            cnt += countRoutes(node.childNodes[i]);
        }
        return cnt;
    }
}
