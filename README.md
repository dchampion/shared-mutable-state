# Observation of Shared Mutable State in Java

## Summary
The small Java program in this repository demonstrates the effects of state changes made in one thread of execution on the visibility of those changes in another thread of execution.

Specifically, this program demonstrates the <i>thread-safety</i> (or lack thereof) of various strategies to change state; specifically, state that is shared between multiple threads.

The documentation&mdash;both in the source code and in this README&mdash;discusses each strategy in turn, and its suitability to particular use cases.

## Requirements
The Java Development Kit (JDK), version 8 or greater, must be installed on your computer, and the location of its binary executables must be in your executable search path.

## Download the Project
* If Git is installed, navigate to a clean directory on your file system and type <code>git clone https<nolink>://github.com/dchampion/shared-mutable-state.git</code>

* If Git is not installed, or you do not wish to use it, click the <code>Clone or download</code> button on this page to download and extract a zipped version of this project into a clean file system directory.

## Build the Project
Using a command-line shell, navigate to the file system directory in which you installed the project and compile the source code:

* If using <code>cmd</code> (Windows), type <code>javac -d bin src\SharedMutableStateDemo.java</code>

* If using <code>Terminal</code> (MacOS) or <code>bash</code> (Linux), type <code>javac -d bin src/SharedMutableStateDemo.java</code>

    <i>Note that these commands are identical save for the direction of the slash; in Windows it is a back-slash ('\\'), and in MacOS or Linux it is a forward-slash ('/')</i>.

* (Optional) To generate the program Javadoc, type <code>javadoc -package -d doc src/SharedMutableStateDemo.java</code> (again, the direction of the slash is OS-dependent). To inspect the Javadoc in a web browser, open <code>/project_root_directory/doc/index.html</code>

## Run the Program
From the project root directory, type <code>java -cp bin SharedMutableStateDemo iterations</code> where <code>iterations</code> is the number of iterations the program will update and read the value of its state variable.

The greater the value of <code>iterations</code>, the more accurate will be the results of the program. This number must be between <code>1</code> and <code>Integer.MAX_VALUE-1</code> (or <code>2147483647</code>). A value of <code>1000000</code> (1M) is a good place to start, as it balances reliability and speed.

## Description
This program performs the following steps:

1. Creates two instances of <code>java.util.Set</code>, a  collection whose invariants guarantee that a) each of its members is unique, and b) each of its members shares the same data type (for the purposes of this program that data type is <code>java.lang.Integer</code>).

2. Creates one instance of a class that produces consecutive whole-numbers. The invariant of this class is that it produces a whole number equal to 1 greater than the last number it produced.

3. Creates two threads, giving each thread access to a) one of the two sets created in step 1, and b) the number-producer created in step 2.

4. Executes, in parallel, both threads created in step 3. Each thread reads values from the number-producer <code>iterations</code> times, and adds those values to its set.

5. On completion of both threads, both sets are analyzed for mutual exclusion.

All of these steps are repeated for each of the thread-safety strategies demonstrated by this program.

A correctly behaving strategy should produce two mutually exclusive sets, the union of which is every consecutive whole number between <code>1</code> to 2x <code>iterations</code> (2x because there are two threads).

The detection of either an intersection or a collision indicates that the strategy is not safe in a program that uses multiple threads.

An intersection is defined as a number appearing in both sets. A collision is defined as an attempt to add a number to a set in which that number already exists.

## Results and Analysis
The following results were produced using the recommended input of 1M (<code>1000000</code>) iterations.
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

#### Analysis
The most obvious thing to note is the large number of intersections and collisions. Recall that even a single occurrence of either condition violates the invariant of the consecutive-number producer, which is that each call to its <code>next()</code> method produce a unique number; namely 1 greater than that produced by its last invocation. <code>Unsynchronized</code> is a badly flawed state-transition strategy.

The problem is that the contents of the <code>next()</code> method are not <i>atomic</i>. Absent atomicity, calls to <code>next()</code> from multiple threads can be interleaved, resulting in reads of state in one thread that have not yet been fully written in the other.

One might wonder how it is that a state change confined to a single line of code&mdash;<code>current++</code>&mdash;can be interleaved; <i>it's one line of code, so it is by definition atomic, right?</i> This is actually not the case. At the assembly level (to which all programming language instructions ultimately reduce) <code>current++</code> requires three operations: 1) read the current value from its memory location into a processor register, 2) add 1 to it and 3) write the updated value back to the original memory location. If one thread yields to another after step 1 (but before step 3), and the other thread is allowed to execute <code>next()</code> in its entirety before yielding back, the first thread will read a value that has already been read.

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

#### Analysis
The only syntactic difference between the <code>Unsynchronized</code> and <code>Volatile</code> strategies is that, in the latter, the state variable <code>current</code> is marked with the <code>volatile</code> keyword. As with the <code>Unsynchronized</code> strategy, we still see a large number of intersections and collisions, making this too a badly flawed state-transition strategy.

The <code>volatile</code> keyword guarantees the <i>visibility</i> of a state change between threads, but it makes no guarantee as to its <i>atomicity</i>. Visibility is only one aspect of thread-safety. In order for a state change to be thread-safe, it must not only be <i>visible</i> to other threads after the state change, but <i>atomic</i> as well.

There are use cases in which the visibility guarantee alone is sufficient for thread safety (e.g. changing a boolean to alert other threads that some event has occurred), but these should not be confused with use cases that demand atomicity as well.

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

#### Analysis
The key difference between the <code>Synchronized</code> strategy and that of the previous two is the presence of the <code>synchronized</code> keyword in the definition of its <code>next()</code> method. The complete absence of intersections and collisions suggests this is a thread-safe state-transition strategy, and it is.

Synchronizing a method in this way guarantees <i>mutually exclusive</i> access to its body, thereby enforcing the atomicity of all the operations within it. When a thread calls the synchronized <code>next()</code> method, it locks access to it, forcing any other thread that calls <code>next()</code> to wait until the owning thread releases the lock. Further, synchronization guarantees that state changes made in one thread are visible to all other threads after the state change has occurred.

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

#### Analysis
<code>SynchronizedBlock</code> is another example of intrinsic locking. For the purposes of this simplified example, it is functionally identical to the <code>Synchronized</code> strategy. In both cases the entire body of the <code>next()</code> method is locked by calling threads.

The synchronized block strategy gives the implementer finer-grained control over the scope of the lock, however. In the case of the consecutive-number producer this is moot, as there is no finer grain than the one-line increment operation <code>current++</code>. But in a more complex method, consisting of tens or hundreds of lines of code, this strategy can be used to confine synchronization to precisely the line(s) of code that change state. Using the <code>synchronized</code> keyword at the method level would be overkill in such a situation, and thereby reduce the <i>concurrency</i> of the program.

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

#### Analysis
The <code>Atomic</code> strategy employs one of several classes in the <code>java.util.concurrent.atomic</code> package specifically designed for thread-safety (in the present case <code>AtomicInteger</code>). These classes enforce atomic state transitions on numbers and object references. As the complete absence of intersections or collisions suggests, this strategy is thread-safe.

In simple cases such as this demonstration, where the entirety of a object's mutable state is confined to a single variable, this strategy is likely preferable to any other because it confines atomicity to precisely the required scope. As the number of state variables increases to more than one, however, the increase in complexity may demand a more robust strategy.

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

#### Analysis
<code>ReentrantLock</code> is the simplest of a number of implementations of the <code>Lock</code> interface found in the <code>java.util.concurrent.locks</code> package. It is also known as an <i>explicit</i> lock (in contrast to the <i>intrinsic</i> locks discussed previously) because it uses an explicit object to enforce mutual exclusion. The <code>Lock</code> implementations provide a richly-featured alternative to the <i>intrinsic</i> locking strategies of <code>Synchronized</code> and <code>SynchronizedBlock</code>.

As with the other thread-safe strategies, implementations of the <code>Lock</code> interface guarantee both the atomicity and visibility of state-changing operations between multiple threads. In addition, they offer advanced features such as timed locked waits, interruptible locked waits and fairness policies (none of which is demonstrated in this program).

The <code>try/finally</code> idiom used to demonstrate the use of <code>ReentrantLock</code> in this example is considered a best practice.