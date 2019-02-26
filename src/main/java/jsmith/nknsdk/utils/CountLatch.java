package jsmith.nknsdk.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class CountLatch {

    private final Object signal = new Object();
    private final AtomicInteger count;

    public CountLatch(int initialCount) {
        this.count = new AtomicInteger(initialCount);
    }

    public void await() throws InterruptedException {
        if (count.get() == 0) return;
        synchronized(signal) {
            while(count.get() > 0) {
                signal.wait();
            }
        }
    }

    public void countUp() {
        count.incrementAndGet();
    }

    public void countDown() {
        synchronized(signal) {
            if (count.getAndUpdate(i -> i > 0 ? i - 1 : 0) == 0) {
                signal.notify();
            }
        }
    }

    public int getCount() {
        return count.get();
    }
}
