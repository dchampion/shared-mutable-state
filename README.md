# The perils of shared mutable state in the Java programming language
## Summary
A small Java program that demonstrates the effects of using different strategies to observe mutable state from separate threads. The documentation, both in the source code and in this README, provides recommendations about which strategy to use, and under what circumstances to use it.

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
* From the project root directory, type <code>java -cp bin SharedMutableStateDemo num_iterations</code>, where <code>num_iterations</code> is the number of times the program will read the shared state variable.

    This command will execute each strategy in turn, and report a) whether it produced consistent results, and b) the elapsed time it took to execute each strategy.

    The greater the value of <code>num_iterations</code>, the more accurate will be the results of the program. This number must be between <code>1</code> and <code>Integer.MAX_VALUE - 1</code> (or 2,147,483,647). A value of <code>1000000</code> (1M) balances reliability and speed.

## Description
The Java code in this demonstration performs the following five steps, once each for a given thread-safety strategy:

1. Creates two instances of <code>java.util.Set</code>, a  collection whose invariants gaurantee that a) each of its members is unique, and b) each of its members shares the same type. The type of both sets is <code>java.lang.Integer</code>.

2. Creates a single instance of a custom whole-number producer. This class implements an interface <code>ConsecutiveNumberProducer</code> whose sole public method is <code>int next()</code>. The contract of this interface is that, starting with the number 0, it will return a whole number equal to 1 greater than the whole number returned by the previous call to <code>next()</code>. That is, it simply produces consecutive whole numbers on successive calls to <code>next()</code>. Each concrete implementation of this class (six in all) employs a different strategy to acheieve thread-safety or, more generally, consistency of state between multiple threads.

3. Creates and executes two threads in parallel, giving each access to a) one of the two sets created in step 1, and b) the single number-producer instance created in step 2. Each thread calls <code>next()</code> on the single, shared number-producer instance and adds the resulting value to its dedicated set (recall that each thread has its own, dedicated set) the number of times specified on the command line; i.e. <code>num_iterations</code> times.

4. On completion of both threads, the sets are passed to a method that counts the number of intersections and/or collisions encounted by each strategy, and reports the results on the command line. An intersection is defined as a whole number appearing in both sets. A collision is defined as an attempt to add a whole number to a set in which that number already exists. In either case, the strategy is not thread-safe.

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
The first and most obvious thing to note is the large number of set intersections. Recall that just one intersection violates the invariant of the consecutive-number generator, which is that each call to its <code>next()</code> method produce a unique number&mdash;namely 1 greater than that produced by its last invocation. Notwithstanding that high standard, the sheer number of intersections&mdash;534,391 among 2,000,000 iterations (1M per thred/set)&mdash;indicates this is a badly flawed strategy.

The second thing to note is the number of set collisions in both threads. Recall that a collision occurs when a single thread attempts to add a number that already appears in its set. As with intersections, even a single collision indicates a flawed thread-safety strategy.

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
The sole difference between the <code>Unsyncrhonized</code> and <code>Volatile</code> strategy is that in the latter the state variable, <code>current</code> is marked with the <code>volatile</code> keyword.

As with the <code>Unsynchronized</code> strategy, we still see a large number of both set intersections and collisions.

While the <code>volatile</code> keyword gaurantees the <i>visiblity</i> of mutable state between threads, it does not gaurantee the <i>atomity</i> of mutations of the variables to which it applies. This strategy still lacks synchronization, without which inter-thread calls to <code>next()</code> will be interleaved, resulting in reads of state in one thread that have not been fully written in the other.

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
The sole difference between the <code>Syncrhonized</code> strategy that of the two previous ones is its use of the <code>synchronized</code> keyword in the definition of its <code>next()</code> method.

