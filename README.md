# The Perils of Shared Mutable State in the Java Programming Language

## Summary
A small Java program that demonstrates the effects of using different strategies to observe mutable state from separate threads. The documentation&mdash;both in the source code and in this README&mdash;provides recommendations about which strategy to use, and under what circumstances to use it.

## Requirements
* The Java Development Kit (JDK), version 8 or greater, must be installed on your computer, and the location of its binary executables must be in your executable search path.

## Download the Project
* If Git is installed, navigate to a clean directory on your file system and type <code>git clone https<nolink>://github.com/dchampion/shared-mutable-state.git</code>.

* If Git is not installed, or you do not wish to use it, click the <code>Clone or download</code> button on this page to download and extract a zipped version of this project into a clean file system directory.

## Build the Project
* Using a command-line shell, navigate the to file system directory in which you downloaded the project; i.e the project root directory.

    * If using <code>cmd</code> (Windows), type <code>javac -d bin src\SharedMutableStateDemo.java</code>.

    * If using <code>Terminal</code> (MacOS) or <code>bash</code> (Linux), type <code>javac -d bin src/SharedMutableStateDemo.java</code>.

    Note the direction of the slash in these commands is OS-dependent; in Windows it is a back-slash ('\\'), and in MacOS or Linux it is a forward-slash ('/').

    The above commands will compile the source code and put the resulting binaries into the <code>bin</code> directory.

    * (Optional) To generate project Javadoc, type <code>javadoc -package -d doc src/SharedMutableStateDemo.java</code> (again, direction of the slash is OS-dependent).
    
    To inspect the Javadoc in a web browser, open <code>/project_root_directory/doc/index.html</code>, where <code>project_root_directory</code> is the project root directory.

## Run the project
From the project root directory, type <code>java -cp bin SharedMutableStateDemo num_iterations</code>, where <code>num_iterations</code> is the number of times the program will read the shared state variable.

This command will execute each strategy in turn, and report a) whether it produced consistent results, and b) the elapsed time it took to execute each strategy.

The greater the value of <code>num_iterations</code>, the more accurate will be the results of the program. This number must be between <code>1</code> and <code>Integer.MAX_VALUE-1</code> (or 2,147,483,647). A value of <code>1000000</code> (1M) is a good place to start as it balances reliability and speed.

## Description
The Java code in this demonstration performs the following steps  for each strategy:

1. Creates two instances of <code>java.util.Set</code>, a  collection whose invariants gaurantee that a) each of its members is unique, and b) each of its members shares the same type. The type of both sets is <code>java.lang.Integer</code>.

2. Creates a single instance of a whole-number producer. This class implements an interface <code>ConsecutiveNumberProducer</code> whose sole public method is <code>int next()</code>. The contract of this interface is that, starting with the number 0, it will return a whole number equal to 1 greater than the whole number returned by the previous call to <code>next()</code>. That is, it produces consecutive whole numbers on successive calls to <code>next()</code>. Each concrete implementation of this class (six in all) employs a different strategy to observe state changes between multiple threads.

3. Creates and executes two threads in parallel, giving each access to a) one of the two sets created in step 1, and b) the single number-producer instance created in step 2. Each thread calls <code>next()</code> on the shared number-producer and adds the resulting value to its dedicated set (recall that each thread gets its own set); it repeats this the number of times specified in <code>num_iterations</code> supplied as a command-line argument.

4. On completion of both threads the sets are analyzed, and the number of intersections and/or collisions encounted by each strategy is reported. An intersection is defined as a whole number appearing in both sets. A collision is defined as an attempt to add a whole number to a set in which that number already exists. In either case, the strategy is not thread-safe.

## Results and Analysis
The following results were produced using the recommended input of 1M (1,000,000) iterations per strategy.
### Unsynchronized Strategy
#### Implementation
<pre style="font-size: 12px">
private int current = 0;

@Override
public int next() {
    if (current == Integer.MAX_VALUE) {
        throw new IllegalStateException("Overflow!");
    }
    return current++;
}
</pre>

#### Result
<pre style="font-size: 12px">
Unsynchronized strategy is not thread-safe.
 -> Total set intersections:          534391
 -> Total collisions in thread/set 1: 2717
 -> Total collisions in thread/set 2: 17821
 -> Total time in milliseconds:       204
</pre>

#### Discussion
The first and most obvious thing to note is the large number of set intersections. Recall that just one intersection violates the invariant of the consecutive-number generator, which is that each call to its <code>next()</code> method produce a unique number&mdash;namely 1 greater than that produced by its last invocation. The sheer number of intersections&mdash;534,391 among 2,000,000 iterations (1M per thread/set)&mdash;indicates this is a badly flawed strategy.

The second thing to note is the number of set collisions in both threads. Recall that a collision occurs when a single thread attempts to add a number that already appears in its set. As with intersections, even a single collision indicates a flawed strategy for observing shared, mutable state.

### Volatile Strategy
#### Implementation
<pre style="font-size: 12px">
private volatile int current = 0;

@Override
public int next() {
    if (current == Integer.MAX_VALUE) {
        throw new IllegalStateException("Overflow!");
    }
    return current++;
}
</pre>

#### Result
<pre style="font-size: 12px">
Volatile strategy is not thread-safe.
 -> Total set intersections:          435794
 -> Total collisions in thread/set 1: 214
 -> Total collisions in thread/set 2: 565
 -> Total time in milliseconds:       183
</pre>

#### Discussion
The sole difference between the <code>Unsyncrhonized</code> and <code>Volatile</code> strategy is that in the latter, the state variable <code>current</code> is marked with the <code>volatile</code> keyword.

As with the <code>Unsynchronized</code> strategy, we still see a large number of both set intersections and collisions.

While the <code>volatile</code> keyword gaurantees the <i>visiblity</i> of mutable state between threads, it does not gaurantee the <i>atomity</i> of mutations of the variables to which it applies. Absent atomicity, inter-thread calls to <code>next()</code> may be interleaved, resulting in reads of state in one thread that have not been fully written in the other.

We also still see a fairly large number of collisions. The <code>volatile</code> keyword gaurantees that a state change made by any thread is immediately visible to any other thread, which might lead one to think this should prevent either thread from reading a value it has previously read. But this is not the case.

### Synchronized Strategy
#### Implementation
<pre style="font-size: 12px">
private int current = 0;

@Override
synchronized public int next() {
    if (current == Integer.MAX_VALUE) {
        throw new IllegalStateException("Overflow!");
    }
    return current++;
}
</pre>

#### Result
<pre style="font-size: 12px">
Synchronized strategy may be thread-safe.
 -> Total set intersections:          0
 -> Total collisions in thread/set 1: 0
 -> Total collisions in thread/set 2: 0
 -> Total time in milliseconds:       264
 </pre>

#### Discussion
The sole difference between the <code>Syncrhonized</code> strategy and that of the two previous ones is its use of the <code>synchronized</code> keyword in the definition of its <code>next()</code> method.

Use of the keyword in this way renders the method mutually exclusive, thereby enforcing atomicity of the operations that occur within it. When a thread calls the synchronized <code>next()</code>, it <i>locks</i> access to the method until it returns. Any thread attempting to call <code>next()</code> while another thread owns this lock will be forced to wait until the method returns in the owning thread. This strategy gaurantees the prevention of intersections and collisions in multiple threads reading shared, mutable state.

This type of synchronization is known as <i>intrinsic</i> locking, because it is a built-in feature of the language.

### SynchronizedBlock Strategy
#### Implementation
<pre style="font-size: 12px">
private int current = 0;

@Override
public int next() {
    synchronized(this) {
        if (current == Integer.MAX_VALUE) {
            throw new IllegalStateException("Overflow!");
        }
        return current++;
    }
}
</pre>

#### Result
<pre style="font-size: 12px">
SynchronizedBlock strategy may be thread-safe.
 -> Total set intersections:          0
 -> Total collisions in thread/set 1: 0
 -> Total collisions in thread/set 2: 0
 -> Total time in milliseconds:       161
 </pre>

#### Discussion
<code>SynchronizedBlock</code> is another example of intrinsic locking, and is (for the purposes of this example) effectively identical to the <code>Synchronized</code> strategy. In both implementations, the entirety of the <code>next()</code> method is locked by calling threads.

The difference is that this strategy gives the implementer finer-grained control over the scope of the lock. In the case of the consecutive-number generator this is moot, as there is no finer grain than the one-line increment operation <code>return current++</code>. But in a more complex method, consisting of several tens of lines of code, for example, this stategy can be used to confine mutual exclusion to just that critical section of code that needs protection. In such a case, using the <code>synchronized</code> keyword on the method is overkill and reduces the overall concurrency of the program.

### Atomic Strategy
#### Implementation
<pre style="font-size: 12px">
final private AtomicInteger current = new AtomicInteger(0);

@Override
public int next() {
    if (current.get() == Integer.MAX_VALUE) {
        throw new IllegalStateException("Overflow!");
    }
    return current.getAndIncrement();
}
</pre>

#### Result
<pre style="font-size: 12px">
Atomic strategy may be thread-safe.
 -> Total set intersections:          0
 -> Total collisions in thread/set 1: 0
 -> Total collisions in thread/set 2: 0
 -> Total time in milliseconds:       231
</pre>

#### Discussion
The <code>Atomic</code> strategy uses one of several classes in the <code>java.util.concurrent.atomic</code> package specifically designed for thread-safety (in the present case <code>AtomicInteger</code>). These classes enforce atomic state transitions on numbers and object references.

In simple cases such as this, where the entirety of a object's mutable state is confined to a single variable, this strategy is likely preferable to any other because it confines the scope of atomicity precisely to the mutable state of the object.

### ReentrantLock Strategy

#### Implementation
<pre style="font-size: 12px">
final private Lock lock = new ReentrantLock();
private int current = 0;

@Override
public int next() {
    lock.lock();
    try {
        if (current == Integer.MAX_VALUE) {
            throw new IllegalStateException("Overflow!");
        }
        return current++;
    } finally {
        lock.unlock();
    }
}
</pre>

#### Result
<pre style="font-size: 12px">
ReentrantLock strategy may be thread-safe.
 -> Total set intersections:          0
 -> Total collisions in thread/set 1: 0
 -> Total collisions in thread/set 2: 0
 -> Total time in milliseconds:       323
</pre>

#### Discussion
A <code>ReentrantLock</code> is the simplest implementation of a number of implementations of the <code>Lock</code> interface found in the <code>java.util.concurrent.locks</code> package. It is also known as an <i>explicit</i> lock, and provides a richly-featured alternative to the <i>intrinsic</i> locking techniques of the <code>Synchronized</code> and <code>SynchronizedBlock</code> strategies.

As with the other thread-safe strategies, if used properly, implementations of the <code>Lock</code> interface gaurantee both atomicity of operations and visibility of state between multiple threads, but in addition offer advanced features such as timed locked waits, interruptible locked waits, and implementation and enforcement of fairness policies. None of these features is used in this program. However, the code used to demonstrate its use here (i.e. the <code>try/finally</code> idiom) is considered a best practice.