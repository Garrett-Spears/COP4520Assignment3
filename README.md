# COP4520Assignment3
Since I am most familiar with Java and had success with it during the first and second assignments, I decided to utilize this language again for this assignment.

# Problem 1: The Birthday Presents Party

## Approach

In this problem, the Minotaur is given a bag of unordered presents with each present having its own unique tag number. In my program, I simulate this unordered bag by first generating a list of integers on the range from 0 to (NUMBER_PRESENTS - 1). Each of these integers represents a unique tag number for a present. I then shuffle this list of present tag numbers to simulate the unorderedness of the presents. Finally, I then transfer this unordered list into a BlockingQueue, so that each servant thread can concurrently access the next present in the unordered bag without causing any issues.

Next, I needed to create an implementation of an ordered chain of presents that the different servant threads could construct and deconstruct simultaneously. After doing some research, I discovered a concurrent lazy linked list implementation in section 9.7 of the textbook: "The Art of Multiprocessor Programming". This implementation allows multiple threads to insert items into a linked list in sorted order without locking the whole list every time or blocking off parts of the list which are drawbacks that occur when using coarse-grained and fine-grained synchronization with concurrent linked lists. I thought it would be advantageous to use the lazy list implementation instead since servant threads never need to obtain a lock when searching/iterating through the list. This greatly improves the efficiency of the list, since many servant threads can insert, remove, and search through the linked list at once as long as they are not trying to modify any same two nodes at a time.

Each of the present nodes in my lazy linked list are of type "PresentNode". Each present node is identified by the tag number for the present. In addition to each node's next reference, each node also has a lock that a servant thread must acquire if it wants to modify this node at all. Differing from the nodes in a fine-grained concurrent linked list, Each present node has a "removed" boolean flag that is set to true whenever a node is removed from the list. This removed flag is useful in the lazy implementation since it lets other threads know whether or not they have a stale reference to a present in the list, so that a thread can retry its operation on the list if the present was just removed.

Also, one thing I did to greatly help with performing operations on this lazy linked list was making the list have a fixed head node with an impossible present tag number to identify it. This means that the list is actually empty when it's size is equal to 1.

### Insertion into Lazy Linked List

I largely used the same insertion method for a lazy linked list that can be found in the textbook, but I did make some minor modifications too. The insertion method of my list implementation receives a present's tag number that needs to be inserted into the list and the method returns whether or not the insertion into the list was possible. The thread then keeps trying to insert this new present into the list in sorted order until it either succeeds or fails. At the beginning of each insertion attempt, a predecessor node reference is assigned the fixed head of the list and another node reference is assigned the first non-head node in the list. The servant then tries iterating both nodes through the list together until the end of the list is reached or until the second node's present tag number becomes greater than or equal to the present tag number that is being inserted.

If the end of the list is reached, then the predecessor node is the last node in the list, so the servant waits to acquire this node's lock. Once this lock is acquired, the servant double checks to see if the last node was removed or if anything was inserted after the last node since acquiring the lock. If so, then the servant thread must try insertion all over again. Otherwise, a new present node is allocated for the tag number passed in and is appended to the end of the list. Then the predecessor node is unlocked and the servant thread can return successfully from insertion.

If the new present needs to be inserted in between two nodes to achieve sorted order, then the servant thread waits to acquire the locks for both the predecessor and current node. After acquiring the lock for both nodes, the servant then checks to see if either of the two nodes have been removed or if something was inserted between the two nodes while waiting for the two locks. If so, the servant thread must try insertion all over again. The servant checks also to see if the second node has a tag number equal to the present tag number trying to be inserted into the list. This should never occur since all present tag numbers should be unique, so the servant thread should release the lock for these two nodes and return that the operation failed. Otherwise, the new present can be inserted in between these two nodes, so a new present node is allocated with the new present tag number and is inserted in between the two locked nodes. Then both locks are released before the servant thread returns successfully from insertion.

### Deletion from Lazy Linked List

For deletion from my concurrent list implementation, I used the same algorithm for deletion on a lazy linked list in the textbook; however, I only ever suport deleting the first node in the list since this is all that the problem really requires for the servants to do. The servant thread keeps trying to remove the first node of the list until it either succeeds/fails, and this deletion method returns the tag number of whatever present was removed from the list (null if nothing could be removed). At the beginning of each deletion attempt, a predecessor node reference is assigned the fixed head of the list and another node reference is assigned the first non-head node in the list.

If there is no first non-head node in the list, then the list is empty so the servant thread returns that the operation has failed since nothing can be removed. Otherwise, the servant waits to acquire the lock for the fixed head node and the following first real present node in the list. After acquiring the lock for both nodes, the servant double checks to see if either of the two nodes have been removed or if something was inserted between the two nodes while waiting for the two locks. If so, the servant thread must try deletion all over again. Otherwise, the servant marks the first non-head node as "removed" and detaches it from the linked list. Then both locks are released before the removed node's tag number is returned successfully from deletion.

### Searching in Lazy Linked List

Searching for a node in a linked list is very efficient in a concurrent lazy linked list. This is due to the fact that no node needs to locked in order to find a node in the list. In this method, the tag number of the present that is being searched for is passed into the "containsPresent" method. First, a predecessor node reference is assigned the fixed head of the list and another node reference is assigned the first non-head node in the list. The servant thne tries iterating both nodes through the list together until the end of the list is reached or until the second node's present tag number becomes greater than or equal to the present tag number that is being searched for. Finally, this method returns true if it did not reach the end of the list, if the first node with a potentially >= tag number is actually equal to the tag number that is being searched for, and if this matching node does not have it's removed flag marked.

This search algorithm does not require any locking. However, before returning, I did insert a condition that checks to see if the predecessor node is currently locked by another servant thread. If so, I make the current servant thread wait to acquire the lock. After acquiring the lock, if the predecessor node wasn't the actual node that was being removed, I simply reassign the second node to point to the predecessor's next node again. This essentially makes the servant wait to see if the node being searched for is currently being inserted to or removed from the list. This check does not cause any significant performance slow-down, and it essentially ensures that the prints are as sequentially as accurate as they can be. Before adding this conditional wait, I noticed that sometimes in my output one servant thread would find a present and print that it's not in the list or is in the list at the same exact time as another servant thread prints that it finally is inserting this same present into the list or removing this same present from the list. Since these two events happen at almost the same exact time, the prints would not always print in a pleasant logical order since one thread may print that it did not find the present immediately after another thread prints that it just inserted this same present into the list. This inconsistency is simply just due to the ordering that the prints are outputted. Although this condition can briefly block searching at times, I discovered that it really resolves logical inconsiticies in the sequential output of different servant threads.



These three core operations of the concurrent lazy linked list are used by all the servant threads to achieve their tasks designated by the minotaur. Each servant is represented by a thread in the program. As the problem specifies, four servant threads are created in total to work together towards writing thank you cards for all the presents. Each servant thread is assigned a unique id (1 - 4), the unordered bag of presents, and the ordered chain of presents (initially empty). Each servant thread runs until all presents have been removed from the unordered bag and there are no presents left in the ordered chain. This signifies that all thank you cards have been written for each present, so the servant thread can simply halt execution. However, during execution, each servant thread randomly alternates between three different tasks: removing present from unordered bag and adding it to the chain of presents in sorted order, removing first present from the ordered chain and writing thank you card for it, and searching if a random present is currently in the ordered chain of presents. Every time a servant thread is ready for a new task, it is randomly assigned one of these three. However, if no presents are currently in the chain, then the servant is assigned the first task automatically since the only thing to do is to remove a present from the unordered bag and add it to the chain. Also, if the unordered bag of presents is empty, then the servant thread is radomly assigned either task two or three since there are no more presents to remove from the bag for task one.

In the main thread, after all four servant threads are initialized and started, then the main thread joins all four of these servant threads, so that it can wait until the servants have finished writing thank you cards for all the presents. After the main thread resumes execution, it simply prints out that all presents have had thank you cards written for them and the exact time it took for the servant threads to do all this processing.

## Generating Output:

I calculate the execution time of the program by recording the time right before all the servant threads are joined together and right after all the servant threads finish execution. At the end of the main thread's execution, it is printed out that all presents have had a thank you card written for them with how long it took for this to occur. 

At the top of the "BirthdayPresents" class, there is a print flag that enables the printing out of all the individual events for each servant. If this is turned on (set to true), then after each completion of a task, a servant will print what they achieved for that task. For the first task, a servant prints out whether or not they succeed inserting a present with its associated tag number into the ordered chain of presents. For the second task, a servant prints out whether or not they were able to remove the first present from the ordered chain, and if successful the servant prints that they wrote a thank you card for this present with its associated tag number. For the third task, the servant just prints out whether or not they found a present with the randomly generated tag number currently in the chain. For each of these task prints, the servant's assigned thread id is also printed out to show which threads are doing what. All these print statements help to give a sequential history of what each servant does and what happens to each present throughout the program's execution. However, this print flag can be set to false if only the final print that all presents have had a thank you card written for them is desired to be printed out.

## Design Correctness/Efficiency

To ensure that my program is working correctly, I simply lowered the number of presents to small constants such as 1, 2, 3, 5, and 10. I then ran my program repeatedly on these small tests with the print steps flag turned on. For each one of these trials, I would verify that the sequential order of output printed out by all servants was logically correct and that the program never finished execution before all presents have had a thank you card written for them. I also ensured that no presents had repeated operations, besides searching, performed on them. After repeating this process many times, I feel confident in my program's design correctness. I also followed a concurrent linked list implementation straight from the textbook, so that I can be confident in its validity.

Overall, I feel that my program is very efficient. I think that I was able to achieve this due to my use of a lazy linked list implementation in my program. With a coarse-grained or fine-grained implementation, I feel that my program's efficiency would be much worse. On the reccomended test of 500,000 presents and no printing individual steps, my program is able to consistently run in under 500ms on my system. The numbers do vary a decent bit, but this is unavoidable since the order of tasks that servants do are randomly assigned. However, with the print flag turned on, my program takes about 4000ms on average to run for the same test of 500,000 presents. This increase in runtime with printing is unavoidable though since printing many things will always just add extra runtime to a program.


## Experimental Evaluation

For my experimental evaluation, I decided to run my program on varying numbers of input with and without the printing of individual steps enabled. I decided to test three input sizes for number of presents and ran five trials for each. The results of these trials are listed below.

    NUM_PRESENTS = 5000
        With Print Steps:
            Trial 1: 69ms
            Trial 2: 70ms
            Trial 3: 70ms
            Trial 4: 69ms
            Trial 5: 68ms
            Average: 69.2ms
        Without Print Steps:
            Trial 1: 12ms
            Trial 2: 10ms
            Trial 3: 9ms
            Trial 4: 11ms
            Trial 5: 10ms
            Average: 10.4ms
    
    NUM_PRESENTS = 50,000
        With Print Steps:
            Trial 1: 439ms
            Trial 2: 452ms
            Trial 3: 449ms
            Trial 4: 443ms
            Trial 5: 458ms
            Average: 448.2ms
        Without Print Steps:
            Trial 1: 87ms
            Trial 2: 112ms
            Trial 3: 110ms
            Trial 4: 95ms
            Trial 5: 109ms
            Average: 102.6ms

    NUM_PRESENTS = 500,000
        With Print Steps:
            Trial 1: 3885ms
            Trial 2: 3847ms
            Trial 3: 3902ms
            Trial 4: 3912ms
            Trial 5: 3903ms
            Average: 3889.8ms
        Without Print Steps:
            Trial 1: 218ms
            Trial 2: 206ms
            Trial 3: 220ms
            Trial 4: 232ms
            Trial 5: 226ms
            Average: 220.4ms

## To Run Problem 1:

Before running, you can modify the number of presents or servants for the problem by changing the value of the NUM_PRESENTS or NUM_SERVANTS field at the top of the "BirthdayPresents" class. You can also enable/disable printing of each servant's task in the program by changing the value of the PRINT_STEPS boolean flag, which is also found at the top of the "BirthdayPresents" class. To run the program:
    1. Use the command prompt to navigate to the directory where the BirthdayPresents.java file is located.
    2. Enter the command "javac BirthdayPresents.java" on the command line to compile the java source code.
    3. Enter the command "java BirthdayPresents" on the command line to execute the code.
    4. Output for the program is printed to the command line.


# Problem 2: Atmospheric Temperature Reading Module

## Approach

For this problem, each sensor is represented by a thread. As the problem specifies there are 8 total sensor threads that run simultaneously during the program's execution. Each sensor thread is responsible for recording 60 random temperatures in the range of -100F to 70F over the span of an hour. Since testing for an hour each time would be very time consuming, I also created a boolean flag at the top of the program that lets you run this same simulation over the span of a minute instead.

Since the problem states that temperature readings from all sensors must be stored in shared memory space, I thought about many different ways I could store all of these temperature readings across different threads in one object. After further analyzing the problem, I discovered that the order of the temperature readings is in fact significant since it asks to keep track of the largest 10 minute interval temperature difference. This led me to finally settle on using a list of concurrent deques to store the temperature readings for each sensor.

Essentially, there are 60 total concurrent deques, one for each minute or second. The idea is that each sensor thread keeps track of the current minute/second that it is recording a temperature for, and this temperature is added to the deque at the current minute/second position.

At the beginning of the program, this list of concurrent deques is constructed. Then, all 8 sensor threads are initialized with each having a reference to this shared memory object. Immediately after, all the 8 sensor threads are started then joined together to pause the main thread's execution until all the sensor threads finish executing.

Each sensor thread takes an hour or minute to run. In a sensor thread's run method, an integer keeps track of the current minute/second from 0-59 during the thread's execution. Every minute/second in this run method, a random temperature in the inclusive range of -100F to 70F is generated. This temperature is then added to the concurrent deque located at the index of the current minute/second in the shared list. The sensor thread is then put to sleep for a minute or second before it repeats this recording process again. Once the sensor thread records all 60 temperatures, the thread can finish execution and return.

Once all sensor threads finish recording their temperatures, the main thread resumes execution and it now has access to all the temperature readings from all sensors. First, a minHeap and maxHeap are created to figure the highest and lowest temperatures recorded. The main thread then iterates through all temperatures record and stores each temperature in both the minHeap and maxHeap. After this, the main thread simply does through all possible intervals in the 60 time readings recorded and keeps track of the interval with the largest temperature difference that was found. Using this data, the program simply prints the top 5 highest and lowest temperatures that were recorded and the largest temperature difference interval that was found.

## Generating Output

As previously mentioned, at the end of the program's execution three things are printed out. First, the top 5 highest temperatures recorded are printed out in order. Second, the top 5 lowest temperatures recorded are printed out in order. Lastly, the largest temperature difference beween any 10 minute interval is printed out. The details of what the temperature started as and went to for this interval are also printed out. No runtime is printed out for this program, since this is simulated to either be right around a minute or hour.

## Design Correctness/Efficiency

To ensure that my program is working correctly, I first ran the program multiple times and printed out all the temperature values recorded by each sensor thread. I verified that these values followed a random pattern and that all (60 * 8) temperatures were recorded (60 for each sensor thread). I also ran my program multiple times and verfied that it always took right around a minute to run. Other than that, the top 5 highest and lowest values seem to be pretty accurate, and the largest temperature interval difference seems to be normal too. It's hard to valid

My program is as efficient as can be since it has to wait for a whole minute or hour to record all the temperatures. The use of efficient concurrent data structures provided by Java makes it so that each sensor thread can quicly store each temperature reading in the shared memory space. The top 5 highest and lowest temperatures are calculated efficiently through the use of a minHeap and maxHeap. The calculation of the largest 10-min temperature difference is calculated efficiently by just parsing through the data and finding the max.

## Experimental Evaluation

For my experimental evaluation, I decided to run my program 5 times and record what output I got for each trial. The results of these trials are listed below:

    Trial 1:
        Top 5 Highest Temperatures: 70, 69, 69, 69, 69
        Top 5 Lowest Temperatures: -100, -99, -99, -99, -99
        Largest 10-minute Interval Temperature Difference: 167
            Beginning Temperature: -99
            End Temperature: 68
    
    Trial 2:
        Top 5 Highest Temperatures: 70, 70, 69, 69, 69
        Top 5 Lowest Temperatures: -100, -100, -100, -99, -98
        Largest 10-minute Interval Temperature Difference: -168
            Beginning Temperature: 69
            End Temperature: -99
    
    Trial 3:
        Top 5 Highest Temperatures: 70, 70, 70, 70, 69
        Top 5 Lowest Temperatures: -100, -100, -99, -99, -97
        Largest 10-minute Interval Temperature Difference: -169
            Beginning Temperature: 70
            End Temperature: -99

    Trial 4:
        Top 5 Highest Temperatures: 70, 70, 69, 69, 69
        Top 5 Lowest Temperatures: -100, -100, -100, -99, -99
        Largest 10-minute Interval Temperature Difference: -168
            Beginning Temperature: 69
            End Temperature: -99

    Trial 5:
        Top 5 Highest Temperatures: 70, 70, 70, 70, 69
        Top 5 Lowest Temperatures: -100, -99, -99, -99, -99
        Largest 10-minute Interval Temperature Difference: -170
            Beginning Temperature: 70
            End Temperature: -100

## To Run Problem 2:

Before running, you can modify whether the program's sensors should recrod 60 times over the span of an hour or a minute. This can be specified by changing the RECORD_HOUR field at the top of the "TemperatureReadings" class. To run the program:
    1. Use the command prompt to navigate to the directory where the TemperatureReadings.java file is located.
    2. Enter the command "javac TemperatureReadings.java" on the command line to compile the java source code.
    3. Enter the command "java TemperatureReadings" on the command line to execute the code.
    4. Wait an hour or minute until results are printed out