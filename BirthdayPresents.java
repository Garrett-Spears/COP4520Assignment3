import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BirthdayPresents {
    public static final int NUM_PRESENTS = 500000;
    private static final int NUM_SERVANTS = 4;
    public static boolean PRINT_STEPS = false;

    public static void main(String[] args) {
        long startTime, endTime;
        List<Integer> presentsList;
        BlockingQueue<Integer> presentsBag;
        LazyLinkedList presentsChain;
        ServantThread[] servantThreads;

        presentsList = new ArrayList<>();
        presentsBag = new ArrayBlockingQueue<>(NUM_PRESENTS);
        presentsChain = new LazyLinkedList();
        servantThreads = new ServantThread[NUM_SERVANTS];

        for (int i = 1; i <= NUM_PRESENTS; i++) {
            presentsList.add(i);
        }

        Collections.shuffle(presentsList);

        for (int i = 0; i < NUM_PRESENTS; i++) {
            presentsBag.offer(presentsList.get(i));
        }

        servantThreads = new ServantThread[NUM_SERVANTS];

        for (int i = 0; i < NUM_SERVANTS; i++) {
            servantThreads[i] = new ServantThread(i + 1, presentsBag, presentsChain);
        }

        for (int i = 0; i < NUM_SERVANTS; i++) {
            servantThreads[i].start();
        }

        startTime = System.currentTimeMillis();
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

        System.out.println("Finished writing thank you cards for all " + NUM_PRESENTS + " presents in " + (endTime - startTime) + "ms!");
    }
}

class ServantThread extends Thread {
    private enum ServantTask {
        ADD_PRESENT_TO_CHAIN,
        WRITE_THANK_YOU_CARD,
        SEARCH_PRESENT_IN_CHAIN
    }

    private int servantId;
    private BlockingQueue<Integer> presentsBag;
    private LazyLinkedList presentsChain;
    private ThreadLocalRandom rand;

    public ServantThread(final int servantId, final BlockingQueue<Integer> presentsBag, final LazyLinkedList presentsChain) {
        this.servantId = servantId;
        this.presentsBag = presentsBag;
        this.presentsChain = presentsChain;
        this.rand = ThreadLocalRandom.current();
    }

    private ServantTask getRandomTask() {
        if (this.presentsBag.isEmpty()) {
            return (this.rand.nextBoolean()) ? ServantTask.WRITE_THANK_YOU_CARD : ServantTask.SEARCH_PRESENT_IN_CHAIN;
        }
        else if (this.presentsChain.isEmpty()) {
            return ServantTask.ADD_PRESENT_TO_CHAIN;
        }

        int randNum = this.rand.nextInt(3);
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

    private boolean moreWorkToDo() {
        return !this.presentsBag.isEmpty() || !this.presentsChain.isEmpty();
    }

    @Override
    public void run() {
        while (moreWorkToDo()) {
            ServantTask currTask = this.getRandomTask();

            if (currTask == ServantTask.ADD_PRESENT_TO_CHAIN) {
                Integer presentTagNum = this.presentsBag.poll();

                if (presentTagNum == null) {
                    continue;
                }

                this.presentsChain.insertPresent(presentTagNum, this.servantId);
            }
            else if (currTask == ServantTask.WRITE_THANK_YOU_CARD) {
                this.presentsChain.removePresent(this.servantId);
            }
            else {
                int randPresentTagNum = this.rand.nextInt(BirthdayPresents.NUM_PRESENTS) + 1;
                boolean foundPresent = this.presentsChain.containsPresent(randPresentTagNum);

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

// Node class for each present in sorted list. Tag number is the unique identifier for each present node. 
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

class LazyLinkedList {
    private final static int HEAD_LIST_TAG_NUM = -1;
    private final PresentNode head;

    public LazyLinkedList() {
        this.head = new PresentNode(HEAD_LIST_TAG_NUM, null);
    }

    private boolean validate(PresentNode pred, PresentNode curr) {
        return !pred.removed && !curr.removed && pred.nextPresentNode == curr;
    }

    public boolean isEmpty() {
        return this.head.nextPresentNode == null;
    }

    public boolean insertPresent(final int presentTagNum, final int servantId) {
        while (true) {
            PresentNode pred = this.head;
            PresentNode curr = this.head.nextPresentNode;

            while (curr != null && presentTagNum < curr.tagNumber) {
                pred = curr;
                curr = curr.nextPresentNode;
            }

            pred.lock.lock();
            try {
                if (curr == null) {
                    if (!pred.removed && pred.nextPresentNode == null) {
                        PresentNode newPresentNode = new PresentNode(presentTagNum, null);

                        if (BirthdayPresents.PRINT_STEPS) {
                            System.out.println("Servant " + servantId + " successfully added present #" + presentTagNum + " to the ordered chain of presents.");
                        }

                        pred.nextPresentNode = newPresentNode;
                        return true;
                    }
                    else {
                        continue;
                    }
                }

                curr.lock.lock();
                try {
                    if (validate(pred, curr)) {
                        if (curr.tagNumber == presentTagNum) {
                            if (BirthdayPresents.PRINT_STEPS) {
                                System.out.println("Servant " + servantId + " failed to add present #" + presentTagNum + " to the ordered chain of presents.");
                            }
                            return false;
                        } 
                        else {
                            PresentNode newPresentNode = new PresentNode(presentTagNum, curr);

                            if (BirthdayPresents.PRINT_STEPS) {
                                System.out.println("Servant " + servantId + " successfully added present #" + presentTagNum + " to the ordered chain of presents.");
                            }

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
        }
    }

    public Integer removePresent(final int servantId) {
        while (true) {
            PresentNode pred = this.head;
            PresentNode first = this.head.nextPresentNode;

            if (first == null) {
                if (BirthdayPresents.PRINT_STEPS) {
                    System.out.println("Servant " + servantId + " could not find any presents in the chain to write thank you cards for at this time.");
                }
                return null;
            }

            pred.lock.lock();
            try {
                first.lock.lock();
                try {
                    if (validate(pred, first)) {
                        first.removed = true;
                        pred.nextPresentNode = first.nextPresentNode;
                        
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

    public boolean containsPresent(final int presentTagNum) {
        PresentNode pred = this.head;
        PresentNode curr = this.head.nextPresentNode;

        while (curr != null && presentTagNum < curr.tagNumber) {
            pred = curr;
            curr = curr.nextPresentNode;
        }

        if (((ReentrantLock) pred.lock).isLocked()) {
            pred.lock.lock();
            if (!pred.removed) {
                curr = pred.nextPresentNode;
            }
            pred.lock.unlock();
        }
        
        boolean foundPresent = curr != null && curr.tagNumber == presentTagNum && !curr.removed;

        return foundPresent;
    }
}