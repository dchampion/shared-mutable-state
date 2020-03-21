import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Java class that demonstrates the effects of different strategies to observe mutable state
 * from separate threads. These strategies range from those that deliver complete enforcement
 * of state consistency (i.e. strategies that are thread-safe) to those that deliver none at all.
 * <p>
 * Without explicit measures to gaurantee that mutable state observed by two or more threads is
 * consistent, that state will likely be inconsistent. The result will be bugs are very difficult
 * to diagnose. This class demonstrates the use of several such measures.
 * <p>
 * While inconsistent state may be a moot concern in a program that runs in a single thread, it
 * will become manifest if and when one or more threads are introduced to the program.
 */
final public class SharedMutableStateDemo {

    /**
     * Strategies&mdash;some thread&ndash;safe, some not&mdash;for reading a mutating state
     * variable from separate threads.
     */
    enum Strategy {
        
        /** Use an <i>unsynchronized</i> method to retrieve a mutating state variable. */
        Unsynchronized,
        
        /**
         * Use an <i>unsynchronized</i> method to retrieve a mutating state variable that
         * is marked <code>volatile</code>.
         */
        Volatile,
        
        /** Use a <i>synchronized</i> method to retrieve a mutating state variable. */
        Synchronized,

        /**
         * Use an <i>unsynchronized</i> method with an embedded synchronized block to
         * retrieve a mutating state variable.
         */
        SynchronizedBlock,
        
        /**
         * Use an <i>unsynchronized</i> method to retrieve a mutating state variable that
         * enforces atomicity of its mutations.
         */
        Atomic,

        /** 
         * Use an <i>unsynchronized</i> method with an embedded explicit lock object to
         * retrieve a mutating state variable.
         */
        ReentrantLock
    }

    public static void main(String[] args) throws Exception {
        // Run and report the results of each strategy in turn.
        readStateFromMultipleThreads(Strategy.Unsynchronized);
        readStateFromMultipleThreads(Strategy.Volatile);
        readStateFromMultipleThreads(Strategy.Synchronized);
        readStateFromMultipleThreads(Strategy.SynchronizedBlock);
        readStateFromMultipleThreads(Strategy.Atomic);
        readStateFromMultipleThreads(Strategy.ReentrantLock);
    }

    /**
     * Exercises a consecutive-number producer in parallel threads to demonstrate the efficacy
     * of different strategies to enforce state consistency.
     * 
     * @param strategy The {@link Strategy} to enforce synchronization.
     * 
     * @throws Exception
     */
    static void readStateFromMultipleThreads(Strategy strategy) throws Exception {
        // Create a brand new ConsecutiveNumberProducer based on the supplied strategy.
        ConsecutiveNumberProducer producer =
            ConsecutiveNumberProducerFactory.newConsecutiveNumberProducer(strategy);
        
        // Create a single thread to produce consecutive numbers in parallel with
        // the current thread.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        // Start the stopwatch.
        long startTime = System.nanoTime();

        // Start reading values from the number producer in the parallel thread.
        Future<Set<Long>> future = executor.submit(() -> readFrom(producer));

        // Start reading values from the same number producer in the current thread.
        Set<Long> mSet = readFrom(producer);
        
        // Wait for the parallel thread to finish.
        Set<Long> tSet = future.get();

        // Stop the stopwatch.
        long totalTime = System.nanoTime() - startTime;
        
        // Report the results of the strategy.
        reportResults(strategy, totalTime, mSet, tSet);
        
        // Shut down the parallel thread executor (otherwise the program will not exit).
        executor.shutdown();
    }

    /**
     * Given a {@link ConsecutiveNumberProducer}, fill a {@link Set} with the consecutive
     * whole numbers it produces over 1M (1,000,000) iterations.
     * 
     * @param producer The {@link ConsecutiveNumberProducer}.
     * 
     * @return A {@link HashSet} containing the numbers produced by the supplied producer.
     */
    static Set<Long> readFrom(ConsecutiveNumberProducer producer) {
        Set<Long> set = new HashSet<>();
        for(int i=0; i<1000000; i++) {
            set.add(producer.next());
        }
        return set;
    }

    /**
     * Given two {@link Set}s containing whole numbers, counts and reports the number of collisions;
     * that is, numbers appearing in both sets. Elapsed time of parallel set population is also reported.
     * <p>
     * If we assume that each of the suppled sets is populated by different threads sharing a single
     * {@link ConsecutiveNumberProducer}, and that a correctly-behaving {@link ConsecutiveNumberProducer}
     * always produces a unique value, then there should be no collisions.
     * 
     * @param strategy The {@link Strategy} used to popuplate the sets.
     * @param mSet The set populated in the main thread.
     * @param tSet The set populated in the parallel thread.
     */
    static void reportResults(Strategy strategy, long totalTime, Set<Long> mSet, Set<Long> tSet) {
        int collisions = 0;
        for (Long item : mSet) {
            if (tSet.contains(item)) {
                collisions++;
            }
        }
        System.out.println("Use of " + strategy + " strategy resulted in " + collisions + " collisions.");
        System.out.println(" -> This strategy " + (collisions == 0 ? "is" : "is not") + " thread-safe, " +
                           "and cost " + (totalTime / 1_000_000) + " milliseconds to retrieve " +
                           (mSet.size() + tSet.size()) + " whole numbers.\n");
    }
}

/**
 * A factory that manufactures {@link ConsecutiveNumberProducer} instances based on a {@link Strategy}.
 */
final class ConsecutiveNumberProducerFactory {
    static ConsecutiveNumberProducer newConsecutiveNumberProducer(SharedMutableStateDemo.Strategy strategy) {
        ConsecutiveNumberProducer producer;
        switch(strategy) {
            case Unsynchronized:
                producer = new UnsynchronizedConsecutiveNumberProducer();
                break;
            case Volatile:
                producer = new VolatileConsecutiveNumberProducer();
                break;
            case Synchronized:
                producer = new SynchronizedConsecutiveNumberProducer();
                break;
            case SynchronizedBlock:
                producer = new SynchronizedBlockConsecutiveNumberProducer();
                break;
            case Atomic:
                producer = new AtomicConsecutiveNumberProducer();
                break;
            case ReentrantLock:
                producer = new ReentrantLockConsecutiveNumberProducer();
                break;
            default:
                producer = new UnsynchronizedConsecutiveNumberProducer();
        }
        return producer;
    }
}

/**
 * An interface to be implemented by classes producing consecutive whole numbers.
 */
interface ConsecutiveNumberProducer {
    
    /**
     * Starting with 0, returns a whole number equal to 1 plus the number
     * returned by the previous call (i.e. 0, 1, 2..., <i>n</i>).
     * 
     * @return The next consecutive whole number.
     */
    Long next();
}

/**
 * This implementation uses an unsychronized method to return the next whole
 * number. While this implementation will behave correctly in a single thread,
 * it will not if it is shared between multiple threads.
 */
final class UnsynchronizedConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    private Long current = Long.valueOf(0);

    @Override
    public Long next() {
        return current++;
    }
}

/**
 * This implementation uses an unsychronized method to return the next whole
 * number, using a variable marked <code>volatile</code>. While this implemenation
 * will behave correctly in a single thread, it will not if it is shared between
 * multiple threads.
 * <p>
 * The <code>volatile</code> keyword gaurantees that the value read from a variable
 * will be the last value written to it by <i>any</i> thread. However, the operation
 * that increments the variable (<code>current++</code>) is actually three operations
 * at the machine-code level (i.e. it is not atomic). This could result in parallel
 * threads retrieving the same value from separate calls to <code>next()</code>.
 */
final class VolatileConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    private volatile Long current = Long.valueOf(0);

    @Override
    public Long next() {
        return current++;
    }
}

/**
 * This implementation uses a synchronized method to return the next whole number,
 * and is thread-safe.
 */
final class SynchronizedConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    private Long current = Long.valueOf(0);

    @Override
    synchronized public Long next() {
        return current++;
    }
}

/**
 * This implementation uses a synchronized block embedded within an unsynchronized method
 * to return the next whole number, and is thread-safe.
 */
final class SynchronizedBlockConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    private Long current = Long.valueOf(0);

    @Override
    public Long next() {
        synchronized(this) {
            return current++;
        }
    }
}

/**
 * This implementation uses an unsynchronized method to return the next whole number,
 * but uses an {@link AtomicLong} to enforce atomicity of changes to its variable's state.
 */
final class AtomicConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    final private AtomicLong current = new AtomicLong(0);

    @Override
    public Long next() {
        return current.getAndIncrement();
    }
}

/**
 * This implementation uses an explicit lock within an unsynchronized method to return
 * the next whole number, and is thread-safe.
 */
final class ReentrantLockConsecutiveNumberProducer implements ConsecutiveNumberProducer {
    final private ReentrantLock lock = new ReentrantLock();
    private Long current = Long.valueOf(0);

    @Override
    public Long next() {
        lock.lock();
        try {
            return current++;
        } finally {
            lock.unlock();
        }
    }
}
