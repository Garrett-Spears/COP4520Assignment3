import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BirthdayPresents {
    public static void main(String[] args) {
        
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

    public boolean insertPresent(final int presentTagNum) {
        while (true) {
            PresentNode pred = this.head;
            PresentNode curr = head.nextPresentNode;

            while (curr != null && curr.tagNumber < presentTagNum) {
                pred = curr;
                curr = curr.nextPresentNode;
            }

            pred.lock.lock();
            try {
                if (curr == null) {
                    if (!pred.removed && pred.nextPresentNode == null) {
                        PresentNode newPresentNode = new PresentNode(presentTagNum, null);
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
                            return false;
                        } 
                        else {
                            PresentNode newPresentNode = new PresentNode(presentTagNum, curr);
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

    public boolean removePresent(final int presentTagNum) {
        while (true) {
            PresentNode pred = this.head;
            PresentNode curr = head.nextPresentNode;

            while (curr != null && curr.tagNumber < presentTagNum) {
                pred = curr;
                curr = curr.nextPresentNode;
            }

            if (curr == null) {
                return false;
            }

            pred.lock.lock();
            try {
                curr.lock.lock();
                try {
                    if (validate(pred, curr)) {
                        if (curr.tagNumber != presentTagNum) {
                            return false;
                        } 
                        else {
                            curr.removed = true;
                            pred.nextPresentNode = curr.nextPresentNode;
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

    public boolean containsPresent(final int presentTagNum) {
        PresentNode curr = this.head;

        while (curr != null && curr.tagNumber < presentTagNum) {
            curr = curr.nextPresentNode;
        }
        
        return curr != null && curr.tagNumber == presentTagNum && !curr.removed;
    }
}