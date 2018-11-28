package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
//	public static void selfTest() {
//		PrioritySchedulerTest.simplePrioritySchedulerTest();
//		PrioritySchedulerTest.complexPriorityDonationTest();
//	}
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

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
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

        // ThreadState owner of the resource's lock
        public ThreadState lock_owner;
        // holds the highest cached priority within the PriorityQueues
        public int return_value = priorityMinimum;
        // holds a list of ThreadState waiting to use the resource
        public ArrayList<ThreadState> lock_pq;
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        PriorityQueue(boolean transferPriority) {

            // initialization of lock_pq
            this.lock_pq = new ArrayList<ThreadState>();
            this.transferPriority = transferPriority;

        }

        /*
        * Inserts the ThreadState of the thread into the waiting queue
        */
        public void waitForAccess(KThread thread) {

            Lib.assertTrue(Machine.interrupt().disabled());

            // adds newly created ThreadState into the waiting queue of the resource
            this.lock_pq.add(getThreadState(thread));

            // run waitForAccess within ThreadState
            getThreadState(thread).waitForAccess(this);
        }

        /*
        * Gains ownership of the resource; can be called before or after priority donation
        */
        public void acquire(KThread thread) {

            Lib.assertTrue(Machine.interrupt().disabled());
            
            // give resource ownership to the thread
            if (this.lock_owner == null) {
                this.lock_owner = getThreadState(thread);
            } else {
                // override cached priority back to its original
                this.lock_owner.after_priority_donation(this);
            }

            getThreadState(thread).acquire(this);
        }
        
        /*
        * Helper function that finds the ThreadState that has
        * the highest cached priority within this PriorityQueue
        */
        public int find_next_thread() {

            // checks whether priority donation is enabled
        	if (this.transferPriority == true) {
                this.return_value = priorityMinimum;
                /*
                * iterates through the resource's waiting queue and
                * finds the next thread after priority donation
                */
        		for (int i = 0; i < this.lock_pq.size(); i++) {
                    int iterate_res_ep = this.lock_pq.get(i).getEffectivePriority();
                    
	            	if (this.return_value < iterate_res_ep) {
	            		this.return_value = iterate_res_ep;
                    }
                    
        		}
        	} else {
        		return priorityMinimum;
        	}
        	
        	return this.return_value;
            
        }

        /*
        * Removes the next thread from the resource's waiting queue and gains ownership of the resource
        */
        public KThread nextThread() {

            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me

            // Get the next thread based on the ThreadState
            ThreadState chosen_thread = this.pickNextThread();

            if (chosen_thread != null) {
                // Remove chosen thread in the waiting queue inside PriorityQueue
                this.lock_pq.remove(chosen_thread);

                // Chosen gains ownership of the resource
                this.acquire(chosen_thread.thread);

                return chosen_thread.thread;
            }

            return null;

        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return	the next thread that <tt>nextThread()</tt> would
         *		return.
        */
        protected ThreadState pickNextThread() {
            // implement me

            if (this.lock_pq.size() == 0) {
                return null;
            } else {
                ThreadState chosen = null;
                int chosen_priority = priorityMinimum;

                /*
                * Gets the next thread based on the cached priority
                */
                for (int i = 0; i < this.lock_pq.size(); i++) {
                    int iterate_thread_eq = this.lock_pq.get(i).getEffectivePriority();

                    if (iterate_thread_eq > chosen_priority || chosen == null) {
                        chosen = this.lock_pq.get(i);
                        chosen_priority = iterate_thread_eq;
                    }

                }
                return chosen;
            }

        }
        
        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }
        
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */

        /** The thread with which this object is associated. */	   
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority;

        // ready queue; stores the resources that the ThreadState is waiting to use
        public ArrayList<PriorityQueue> res_have;
        // cached priority inside ThreadState
        public int cached_priority;
        // waiting queue; ThreadStates waiting in line to use these resources
        public ArrayList<PriorityQueue> res_wait;

        public ThreadState(KThread thread) {
            this.thread = thread;
            
            // initialization for resource holders
            // ready queue
            this.res_have = new ArrayList<PriorityQueue>();
            // wait queue
            this.res_wait = new ArrayList<PriorityQueue>();
            
            setPriority(priorityDefault);
            
            // sets effective priority
            this.cached_priority = this.getPriority();

        }

        /*
        * Helper function that removes access to the resource within the TheadState
        * and changes cached priority back
        */
        public void after_priority_donation(PriorityQueue waitQueue) {
        	
        	// access to resource no longer needed; thread done using it
            this.res_have.remove(waitQueue);
            
            // resets cached priority back to its original priority
            this.cached_priority = getPriority();
            
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return	the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return	the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            // implement me
            
            
            if (this.res_have.size() != 0) {
                this.cached_priority = this.getPriority();
                /*
                * finds the thread inside the waiting queue of the resource's lock and find the one
                * with the largest cached priority
                */
	            for (int i = 0; i < this.res_have.size(); i++) {
                    int iterate_res_ep = this.res_have.get(i).find_next_thread();
	            	if (this.cached_priority < iterate_res_ep) {
	            		this.cached_priority = iterate_res_ep;
                    }
                    
                }
                
	            return this.cached_priority;
            }
            
            return getPriority();

        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param	priority	the new priority.
         */
        public void setPriority(int priority) {
            // implement me

            if (this.priority == priority) {
                return;
            }
            
            this.priority = priority;
            
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param	waitQueue	the queue that the associated thread is
         *				now waiting on.
        *
        * @see	nachos.threads.ThreadQueue#waitForAccess
        */
        public void waitForAccess(PriorityQueue waitQueue) {
            // implement me

            // removes the lock's priority queue from the ready queue of the ThreadState (if it exists inside)
            this.res_have.remove(waitQueue);

            // adds the lock's priority queue to the waiting queue of the ThreadState
            this.res_wait.add(waitQueue);

        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see	nachos.threads.ThreadQueue#acquire
         * @see	nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            // implement me

            // releases lock from ready queue
            this.res_have.remove(waitQueue);

            // adds resource in ready queue
            this.res_have.add(waitQueue);

        }	

    }
}
