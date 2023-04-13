import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BirthdayPresents {
    // Parameters that define the number of presents to be processed and the number of threads/servants 
    // that will take care of the processing
    public static final int NUM_PRESENTS = 500000;
    private static final int NUM_SERVANTS = 4;

    // Boolean flag that decides whether or not each servant's task should be printed out during the program's execution
    public static boolean PRINT_STEPS = false;

    public static void main(String[] args) {
        long startTime, endTime;
        List<Integer> presentsList;
        BlockingQueue<Integer> presentsBag;
        LazyLinkedList presentsChain;
        ServantThread[] servantThreads;

        // Initialize all objects needed for the problem
        presentsList = new ArrayList<>();
        presentsBag = new ArrayBlockingQueue<>(NUM_PRESENTS);
        presentsChain = new LazyLinkedList();
        servantThreads = new ServantThread[NUM_SERVANTS];

        // Create a sorted list of all the present's tag numbers
        for (int i = 1; i <= NUM_PRESENTS; i++) {
            presentsList.add(i);
        }

        // Shuffle the list of present tag numbers to simulate the unordered bag of presents
        Collections.shuffle(presentsList);

        // Redistribute this unordered list of tag numbers into a thread-safe queue
        for (int i = 0; i < NUM_PRESENTS; i++) {
            presentsBag.offer(presentsList.get(i));
        }

        servantThreads = new ServantThread[NUM_SERVANTS];

        // Initialize all servant threads
        for (int i = 0; i < NUM_SERVANTS; i++) {
            servantThreads[i] = new ServantThread(i + 1, presentsBag, presentsChain);
        }

        // Start each servant thread
        for (int i = 0; i < NUM_SERVANTS; i++) {
            servantThreads[i].start();
        }

        startTime = System.currentTimeMillis();

        // Join all servant threads so that main thread waits until all servants finish processing all the presents
        try {
            for (int i = 0; i < NUM_SERVANTS; i++) {
                servantThreads[i].join();
            }
        }
        catch (InterruptedException e) {
            System.out.println("Error joining thread: " + e.toString());
            return;
        }

        endTime = System.currentTimeMillis();

        // All the presents have been processed, so print out how long it took to write thank you cards for all these presents
        System.out.println("Finished writing thank you cards for all " + NUM_PRESENTS + " presents in " + (endTime - startTime) + "ms!");
    }
}

class ServantThread extends Thread {
    // Defines the 3 tasks that the servants can randomly choose between
    private enum ServantTask {
        ADD_PRESENT_TO_CHAIN,
        WRITE_THANK_YOU_CARD,
        SEARCH_PRESENT_IN_CHAIN
    }

    // Each servant is assigned a unique identifier and assigned references to the 
    // unordered bag of presents and the chain of presents that is constructed
    private int servantId;
    private BlockingQueue<Integer> presentsBag;
    private LazyLinkedList presentsChain;

    public ServantThread(final int servantId, final BlockingQueue<Integer> presentsBag, final LazyLinkedList presentsChain) {
        this.servantId = servantId;
        this.presentsBag = presentsBag;
        this.presentsChain = presentsChain;
    }

    // Returns a random task that the servant can do next
    private ServantTask getRandomTask() {
        // No presents are left in the bag, so the servant can randomly choose between writing a
        // thank you card or checking if a present is currently in the chain
        if (this.presentsBag.isEmpty()) {
            return (ThreadLocalRandom.current().nextBoolean()) ? ServantTask.WRITE_THANK_YOU_CARD : ServantTask.SEARCH_PRESENT_IN_CHAIN;
        }
        // No presents are currently inserted in the chain, so the only thing that the servant can do
        // right now is add a present to the chain
        else if (this.presentsChain.isEmpty()) {
            return ServantTask.ADD_PRESENT_TO_CHAIN;
        }

        // Otherwise, the servant can randomly choose between doing any of the three tasks
        int randNum = ThreadLocalRandom.current().nextInt(3);
        if (randNum == 0) {
            return ServantTask.ADD_PRESENT_TO_CHAIN;
        }
        else if (randNum == 1) {
            return ServantTask.WRITE_THANK_YOU_CARD;
        }
        else {
            return ServantTask.SEARCH_PRESENT_IN_CHAIN;
        }
    }

    // Returns true if there are any more presents left that need a thank you card written
    private boolean moreWorkToDo() {
        return !this.presentsBag.isEmpty() || !this.presentsChain.isEmpty();
    }

    @Override
    public void run() {
        // Servant should keep randomly choosing tasks until thank 
        // you cards have been written for all the presents
        while (moreWorkToDo()) {
            ServantTask currTask = this.getRandomTask();

            if (currTask == ServantTask.ADD_PRESENT_TO_CHAIN) {
                // Get the next present from the unordered bag
                Integer presentTagNum = this.presentsBag.poll();

                // Another servant/thread must have removed the last present right before, so try choosing another task to do
                if (presentTagNum == null) {
                    continue;
                }

                this.presentsChain.insertPresent(presentTagNum, this.servantId);
            }
            else if (currTask == ServantTask.WRITE_THANK_YOU_CARD) {
                // Just try to remove the first present in the ordered chain and write a thank you card for it if successful
                this.presentsChain.removePresent(this.servantId);
            }
            else {
                // Pick any random present out of all the presents originally in the bag and check whether or not
                // it is currently found in the chain
                int randPresentTagNum = ThreadLocalRandom.current().nextInt(BirthdayPresents.NUM_PRESENTS) + 1;
                boolean foundPresent = this.presentsChain.containsPresent(randPresentTagNum);

                // If print flag is turned on, print whether or not this present was found in the chain
                if (BirthdayPresents.PRINT_STEPS) {
                    if (foundPresent) {
                        System.out.println("Servant " + servantId + " found present #" + randPresentTagNum + " in the ordered chain of presents.");
                    }
                    else {
                        System.out.println("Servant " + servantId + " did not find present #" + randPresentTagNum + " in the ordered chain of presents.");
                    }
                }
            }
        }
    }
}

// Node class for each present in the sorted chain. Tag number is the unique identifier for each present node. 
// Each present also has its own removed flag to let other threads know whether or not they are holding onto a stale reference to this present.
class PresentNode {
    public final int tagNumber;
    public PresentNode nextPresentNode;
    public boolean removed;
    public final Lock lock;

    public PresentNode(final int tagNumber, final PresentNode nextPresentNode) {
        this.tagNumber = tagNumber;
        this.nextPresentNode = nextPresentNode;
        this.removed = false;
        this.lock = new ReentrantLock();
    }
}

// This lazy list implementation was heavily inspired by the implementation that can be found 
// in section 9.7 of the text book ("The Art of Multiprocessor Programming"). It is used as the ordered
// chain of presents that is constructed by the servants in this program.
class LazyLinkedList {
    // Impossible present tag number that can be used for the head node of the list
    private final static int HEAD_LIST_TAG_NUM = -1;

    private final PresentNode head;

    public LazyLinkedList() {
        // Initialize head of list to empty present node that will always be fixed at the front of the list
        this.head = new PresentNode(HEAD_LIST_TAG_NUM, null);
    }

    // Checks whether or not current and predecessor node have been removed or if the connection
    // between these two nodes have been broken to ensure validity of this piece of the list
    private boolean validate(PresentNode pred, PresentNode curr) {
        return !pred.removed && !curr.removed && pred.nextPresentNode == curr;
    }

    // Returns whether or not list is empty based on whether or not any present nodes follow the fixed head node
    public boolean isEmpty() {
        return this.head.nextPresentNode == null;
    }

    // Tries to insert new present into the chain of presents in its sorted position and returns whether or not it was successful
    public boolean insertPresent(final int presentTagNum, final int servantId) {
        // Keep trying to insert present until success or failure
        while (true) {
            // Get the first two nodes of the list
            PresentNode pred = this.head;
            PresentNode curr = this.head.nextPresentNode;

            // Keep iterating through the list until the end is reached or until the 
            // two nodes that the new present should be inserted between are found
            while (curr != null && presentTagNum < curr.tagNumber) {
                pred = curr;
                curr = curr.nextPresentNode;
            }

            pred.lock.lock();
            try {
                // If at the end of the list, only need the final node to be locked, so insert the new present at the end of the list
                if (curr == null) {
                    // Make sure last node in list has not been removed and that nothing has been appended to end of list since
                    if (!pred.removed && pred.nextPresentNode == null) {
                        // Initialize the new present node to point to "null" since it is the new end node of the list
                        PresentNode newPresentNode = new PresentNode(presentTagNum, null);

                        // If print flag is turned on, print that the present was successfully inserted
                        if (BirthdayPresents.PRINT_STEPS) {
                            System.out.println("Servant " + servantId + " successfully added present #" + presentTagNum + " to the ordered chain of presents.");
                        }

                        // Make the last node in the list now point to this new last node
                        pred.nextPresentNode = newPresentNode;
                        return true;
                    }
                    // Otherwise, try again
                    else {
                        continue;
                    }
                }

                curr.lock.lock();
                try {
                    // Make sure that the two nodes that present is going to be inserted in between are still in the list and connected
                    if (validate(pred, curr)) {
                        // Already found present in list but each present should be unique, so don't insert this duplicate present
                        if (curr.tagNumber == presentTagNum) {
                            // If print flag is turned on, print that the present failed to be inserted
                            if (BirthdayPresents.PRINT_STEPS) {
                                System.out.println("Servant " + servantId + " failed to add present #" + presentTagNum + " to the ordered chain of presents.");
                            }
                            return false;
                        } 
                        else {
                            // Initialize the new present node to point to the first node in the list that has a larger tag number
                            PresentNode newPresentNode = new PresentNode(presentTagNum, curr);

                            // If print flag is turned on, print that the present was successfully inserted
                            if (BirthdayPresents.PRINT_STEPS) {
                                System.out.println("Servant " + servantId + " successfully added present #" + presentTagNum + " to the ordered chain of presents.");
                            }

                            // Make the predecessor node point to this new present with the next largest tag number
                            pred.nextPresentNode = newPresentNode;
                            return true;
                        }
                    }
                } 
                finally {
                    curr.lock.unlock();
                }
            } 
            finally {
                pred.lock.unlock();
            }
            // Unlock both nodes as needed before returning or before attempting to insert into the list again
        }
    }

    // Tries to remove the first present in the chain of presents. Returns the remove present's tag number if successful
    // and null if not successful
    public Integer removePresent(final int servantId) {
        // Keep trying to remove first present until success or failure
        while (true) {
            // Get the fixed head node and the first present node in the list
            PresentNode pred = this.head;
            PresentNode first = this.head.nextPresentNode;

            // If no first present node is found, then there is no presents to remove from the list
            if (first == null) {
                // If print flag is turned on, print that no present could be removed from the chain
                if (BirthdayPresents.PRINT_STEPS) {
                    System.out.println("Servant " + servantId + " could not find any presents in the chain to write thank you cards for at this time.");
                }
                return null;
            }

            pred.lock.lock();
            try {
                first.lock.lock();
                try {
                    // Make sure that the first present node hasn't been removed yet and that this
                    // present node is still the first present in the list
                    if (validate(pred, first)) {
                        // Mark that this present node is now being removed in case another servant/thread
                        // is still referencing it
                        first.removed = true;

                        // Set fixed head node to point to node right after this present, removing it from the list
                        pred.nextPresentNode = first.nextPresentNode;
                        
                        // If print flag is turned on, print the tag number of this first present that was successfully removed
                        if (BirthdayPresents.PRINT_STEPS) {
                            System.out.println("Servant " + servantId + " successfully wrote thank you card for present #" + first.tagNumber + ".");
                        }

                        return first.tagNumber;
                    }
                } 
                finally {
                    first.lock.unlock();
                }
            } 
            finally {
                pred.lock.unlock();
            }
            // Unlock both nodes before returning or before attempting to remove the chain's first present again
        }
    }

    // Delete Later
    // public boolean removePresent(final int presentTagNum) {
    //     while (true) {
    //         PresentNode pred = this.head;
    //         PresentNode curr = head.nextPresentNode;

    //         while (curr != null && curr.tagNumber < presentTagNum) {
    //             pred = curr;
    //             curr = curr.nextPresentNode;
    //         }

    //         if (curr == null) {
    //             return false;
    //         }

    //         pred.lock.lock();
    //         try {
    //             curr.lock.lock();
    //             try {
    //                 if (validate(pred, curr)) {
    //                     if (curr.tagNumber != presentTagNum) {
    //                         return false;
    //                     } 
    //                     else {
    //                         curr.removed = true;
    //                         pred.nextPresentNode = curr.nextPresentNode;
    //                         return true;
    //                     }
    //                 }
    //             } 
    //             finally {
    //                 curr.lock.unlock();
    //             }
    //         } 
    //         finally {
    //             pred.lock.unlock();
    //         }
    //     }
    // }

    // Checks whether or not present with this tag number is in the chain
    public boolean containsPresent(final int presentTagNum) {
        // Get the first two nodes of the list
        PresentNode pred = this.head;
        PresentNode curr = this.head.nextPresentNode;

        // Keep iterating through the chain until the end is reached or until a 
        // node with a tag number >= the tag number passed in is found
        while (curr != null && presentTagNum < curr.tagNumber) {
            pred = curr;
            curr = curr.nextPresentNode;
        }

        // If predecessor node is currently being modified/affected by an insertion or deletion into the list, 
        // then wait until this operation is done to get the most up-to-date results in-case the desired present 
        // may be in the process of being inserted to or removed from the list
        if (((ReentrantLock) pred.lock).isLocked()) {
            pred.lock.lock();

            // As long as predecessor was not removed from the list, then update the following target
            // node to get the most up-to-date results of whether or not the present is in the list
            if (!pred.removed) {
                curr = pred.nextPresentNode;
            }

            pred.lock.unlock();
        }
        
        // Present was found if did not reach the end of the list while searching, and the
        // target node's tag number is equal to the desired present tag number, and
        // this target node was not marked as removed since
        boolean foundPresent = curr != null && curr.tagNumber == presentTagNum && !curr.removed;

        return foundPresent;
    }
}