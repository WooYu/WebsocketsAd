package com.oohlink.player.sdk.socket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存等待回执的消息
 * 消息数量超过指定容量时，移除旧的消息
 */
public class MessageProcessor<K, V> {
    /**
     * 存储消息的映射，键是seq,值是对象，比如MessageRetryTask
     */
    private final ConcurrentHashMap<K, V> messageMap = new ConcurrentHashMap<>();
    /**
     * 有界队列用于存储seq，以保持插入的顺序，用于内存不足时，消息的移除
     */
    private final ConcurrentLinkedQueue<K> seqQueue = new ConcurrentLinkedQueue<>();
    /**
     * 当前队列的大小
     */
    private final AtomicInteger size = new AtomicInteger(0);
    /**
     * 当前队列的最大容量
     */
    private final int capacity;
    /**
     * 锁，用于确保对seqQueue和size的操作是原子性的
     */
    private final ReentrantLock lock = new ReentrantLock();

    public MessageProcessor(int capacity) {
        this.capacity = capacity;
    }

    /**
     * 添加消息到队列中
     */
    public boolean addMessage(K key, V value) {
        boolean added = false;
        if (key == null) {
            return false;
        }
        lock.lock();
        try {
            while (size.get() >= capacity) {
                //如果队列已满，移除最旧的消息
                K oldestSeq = seqQueue.poll();
                if (null != oldestSeq) {
                    messageMap.remove(oldestSeq);
                    size.decrementAndGet();
                }
            }
            if (messageMap.putIfAbsent(key, value) == null) {
                seqQueue.offer(key);
                size.incrementAndGet();
                added = true;
            }
        } finally {
            lock.unlock();
        }
        return added;
    }

    /**
     * 根据key移除消息
     */
    public V removeMessage(K key) {
        lock.lock();
        try {
            V message = messageMap.remove(key);
            if (message != null) {
                seqQueue.remove(key);
                size.decrementAndGet();
            }
            return message;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据key获取消息
     */
    public V getMessage(K key) {
        return messageMap.get(key);
    }

    /**
     * 获取当前队列大小
     */
    public int getSize() {
        return size.get();
    }

    public void clear() {
        seqQueue.clear();
        messageMap.clear();
        size.set(0);
    }
}
