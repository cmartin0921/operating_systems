package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Random;

//import nachos.threads.PriorityScheduler;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	// implement me
        return new LotteryPriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getTickets();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        return getThreadState(thread).getEffectiveTickets();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);
        getThreadState(thread).setTickets(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();
        int priority = getTickets(thread);
        if (priority == priorityMaximum)
            return false;

        setTickets(thread, priority + 1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();
        
        KThread thread = KThread.currentThread();
        int priority = getTickets(thread);
        if (priority == priorityMinimum)
            return false;
        
        setPriority(thread, priority - 1);
        Machine.interrupt().restore(intStatus);
        return true;
        
    }

    /**
    * The default priority for a new thread. Do not change this value.
    */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 1;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
    * Return the scheduling state of the specified thread.
    *
    * @param	thread the thread whose scheduling state to return.
    * @return	the scheduling state of the specified thread.
    */
    protected class LotteryPriorityQueue extends ThreadQueue {

        // ThreadState owner of the resource's lock
        public LotteryThreadState lock_owner;
        // holds a list of LotteryThreadState waiting to use the resource
        public ArrayList<LotteryThreadState> waiting_queue;
        // holds the total amount of cached tickets inside the waiting queue
        public int total_cached_tickets;
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        LotteryPriorityQueue(boolean transferPriority) {
            this.waiting_queue = new ArrayList<LotteryThreadState>();
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {

            Lib.asserTrue(Machine.interrupt().disabled());

            // adds newly created ThreadState into the waiting queue of the resource
            this.waiting_queue.add(getThreadState(thread));
            this.calculate_cached_ticket_total();

            // run waitForAccess within ThreadState
            getThreadState(thread).waitForAccess(this);

        }

        public void acquire(KThread thread) {

            Lib.assertTrue(Machine.interrupt().disabled());

            // give resource ownership to the thread
            if (this.lock_owner == null) {
                this.lock_owner = getThreadState(thread);
            } else {
                // TODO: implement the function called in here inside LotteryThreadState
                this.lock_owner.after_priority_donation(this);
            }

            getThreadState(thread).acquire(this);
            
        }

        public KThread nextThread() {
            
            Lib.assertTrue(Machine.interrupt().disabled());

            // TODO: implement me
            LotteryThreadState chosen_thread = this.pickNextThread();

            if (chosen_thread != null) {
                // Remove chosen thread in the waiting queue inside LotteryPriorityQueue
                this.waiting_queue.remove(chosen_thread);
                // Called since a thread was removed inside the waiting queue
                // TODO: need to go through each thread that has this waiting queue
                // inside its res_have
                calculate_cached_ticket_total();

                // Chosen gains ownership of the resource
                this.acquire(chosen_thread.thread);

                return chosen_thread.thread;
            }

            return null;

        }

        public void calculate_cached_ticket_total() {

            // TODO: implement me
            if (this.transferPriority != true) {
                return;
            }

            int ticket_summation = 0;
            for (int i = 0; i < waiting_queue.size(); i++) {
                LotteryThreadState curr_lts = waiting_queue.get(i);
                ticket_summation += curr_lts.cached_tickets;    
            }

            total_cached_tickets = ticket_summation;

        }

        protected LotteryThreadState pickNextThread() {

            // TODO: implement me
            if (this.waiting_queue.size() == 0) {
                return null;
            }

            // If we want to make sure that the total cached tickets is properly updated
            // calculate_cached_ticket_total();

            int summation = 0;
            Random rng = new Random();
            int winning_ticket = rng.nextInt(total_cached_tickets) + 1;
            for (int i = 0; i < waiting_queue.size(); i++) {
                LotteryThreadState curr_lts_cached_ticket = this.waiting_queue.get(i);
                summation += curr_lts_cached_ticket.getEffectiveTickets();
                
                if (summation >= winning_ticket) {
                    return curr_lts_cached_ticket;
                }
                
            }

            return null;

        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
        }

    }

    protected class LotteryThreadState extends ThreadState {

        protected int tickets;
        public int cached_tickets;

        public LotteryThreadState(KThread thread) {
            this.res_have = new ArrayList<LotteryPriorityQueue>();
            this.res_wait = new ArrayList<LotteryPriorityQueue>();
            this.tickets = priorityDefault;
            this.cached_tickets = this.getTickets();
        }

        public int getEffectiveTickets() {
            // TODO: implement me
        }

        public int getTickets() {
            return tickets;
        }

        public void after_priority_donation(LotteryPriorityQueue waitQueue) {
            // TODO: implement me
            // Remember to implement any updates needed. Any additions/removals from
            // any queue calls for recalculation
        }

        public void waitForAccess(LotteryPriorityQueue waitQueue) {
            // TODO: implement me
        }

        public void acquire(LotteryPriorityQueue waitQueue) {
            // TODO: implement me
        }

    }

}
