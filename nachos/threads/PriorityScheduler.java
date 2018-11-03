package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p/>
 * <p/>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p/>
 * <p/>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p/>
 * <p/>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks,` and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
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
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
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

        // waiting queue of ThreadState
        protected final List<ThreadState> waiting_queue;

        // thread that currently has the lock due to having access to the critical code
        protected ThreadState thread_lock = null;

        protected int effectivePriority = priorityMinimum;

        protected int priority_change = 0;
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
        */
        public boolean transferPriority;

        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
            this.waiting_queue = new LinkedList<ThreadState>();
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            
			// adds newly created thread state into the waiting queue
			this.waiting_queue.add(getThreadState(thread));

			// run waitForAccess within ThreadState
		    getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            // TODO: remove release
			if (this.thread_lock != null) {
				this.thread_lock.release(this);
			}

			// checks if no thread has the lock
			if (this.thread_lock == null) {
				this.thread_lock = getThreadState(thread);
			}

            getThreadState(thread).acquire(this);

        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

			// Get the next thread based on the ThreadState
			ThreadState chosen_thread = this.pickNextThread();

			if (chosen_thread != null)
			{
				// Remove chosen thread in the waiting queue
				this.waiting_queue.remove(chosen_thread);

				// Chosen thread acquires the resource
				this.acquire(chosen_thread.thread);

				return chosen_thread.thread;

			}
			else
			{
				return null;
			}
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         *         return.
         */
        protected ThreadState pickNextThread() {
            // implement me

            ThreadState chosen = null;
			int chosen_priority = priority_minimum;

			// checks if no thread state is within the waiting queue
			if (this.waiting_queue.isEmpty())
			{
				return null;
			}
			else
			{	
				// iterates through the thread states in the waiting queue and finds the one with the highest priority
				for (i = 0; i < this.waiting_queue.size(); i++)
				{
					ThreadState iterate_thread = waiting_queue.get(i);
	
					if (iterate_thread.getEffectivePriority() > chosen_priority)
					{
						chosen = waiting_queue.get(i);
						chosen_priority = iterate_thread.getEffectivePriority(); 
					}
	
				}
			}

		    return chosen;

        }

        /**
         * This method returns the effectivePriority of this PriorityQueue.
         * The return value is cached for as long as possible. If the cached value
         * has been invalidated, this method will spawn a series of mutually
         * recursive calls needed to recalculate effectivePriorities across the
         * entire resource graph.
         * @return
         */

         // TODO: get rid of effective priority inside PriorityQueues
        public int getEffectivePriority() {
            if (!this.transferPriority) {
                return priorityMinimum;
            } else if (this.priority_change == 1) {
                // Recalculate effective priorities
                this.effectivePriority = priorityMinimum;
                for (final ThreadState curr : this.waiting_queue) {
                    this.effectivePriority = Math.max(this.effectivePriority, curr.getEffectivePriority());
                }
                this.priority_change = 0;
            }
            return effectivePriority;
        }

        public void print() {
            // implement me (if you want)
        }

        private void invalidateCachedPriority() {
            if (!this.transferPriority) return;

            this.priority_change = 1;

            if (this.thread_lock != null) {
                thread_lock.invalidateCachedPriority();
            }
        }

    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */

        /**
         * The thread with which this object is associated.
        */
        protected KThread thread;
        /**
         * The priority of the associated thread.
        */
        protected int priority;

        // keeps track of the number of ThreadStates there are 
		private int thread_state_counter = 1;

		// tie-breaker that determines which ThreadStates that have the same priority goes first
		protected int pass;

        protected int priority_change = 0;

        protected int effectivePriority = priorityMinimum;

        protected final List<PriorityQueue> resourcesIHave;

        protected final List<PriorityQueue> resourcesIWant;

        public ThreadState(KThread thread) {
            this.thread = thread;

            this.resourcesIHave = new LinkedList<PriorityQueue>();
            this.resourcesIWant = new LinkedList<PriorityQueue>();
            pass = thread_state_counter;
			thread_state_counter++;

            setPriority(priorityDefault);

        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {

            if (this.resourcesIHave.isEmpty()) {
                return this.getPriority();
            } else if (this.priority_change == 1) {
                this.effectivePriority = this.getPriority();
                for (final PriorityQueue pq : this.resourcesIHave) {
                    this.effectivePriority = Math.max(this.effectivePriority, pq.getEffectivePriority());
                }
                this.priority_change = 0;
            }
            return this.effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;
            this.priority = priority;
            // force priority invalidation
            for (final PriorityQueue pq : resourcesIWant) {
                pq.invalidateCachedPriority();
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            this.resourcesIWant.add(waitQueue);
            this.resourcesIHave.remove(waitQueue);
            waitQueue.invalidateCachedPriority();
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            this.resourcesIHave.add(waitQueue);
            this.resourcesIWant.remove(waitQueue);
            this.invalidateCachedPriority();
        }

        /**
         * Called when the associated thread has relinquished access to whatever
         * is guarded by waitQueue.
          * @param waitQueue The waitQueue corresponding to the relinquished resource.
         */
        public void release(PriorityQueue waitQueue) {
            this.resourcesIHave.remove(waitQueue);
            this.invalidateCachedPriority();
        }

        public KThread getThread() {
            return thread;
        }

        private void invalidateCachedPriority() {
            if (this.priority_change == 1) return;
            this.priority_change = 1;
            for (final PriorityQueue pq : this.resourcesIWant) {
                pq.invalidateCachedPriority();
            }
        }

    }
}
