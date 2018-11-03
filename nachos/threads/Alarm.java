package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {

    	//If the queue is empty just return
    	if(waitUntilQueue.isEmpty()) {
    		return;
    	}
    	//If the first value of the queue, ie the value that has the shortest wait time
    	//has a time greater than or equal to the Machine's time don't do anything and return
    	if(waitUntilQueue.peek().getTime()>=Machine.timer().getTime()) {
    		return;
    	}
    	//Disable interrupts 
    	boolean intStatus=Machine.interrupt().disable();
    	//If wait queue is not empty and the first threads time is less than the machine's time
    	while(!waitUntilQueue.isEmpty() && waitUntilQueue.peek().getTime() <= Machine.timer().getTime()) {
    		//Remove the first element of the wait priority queue and put its thread on the ready queue
    		waitUntilQueue.poll().getThread().ready();
    	}
    	//Restore interrupts
    	Machine.interrupt().restore(intStatus);
    	//Yield the current thread
    	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
    	long wakeTime = Machine.timer().getTime() + x;
    	//Disable interrupts
    	boolean intStatus = Machine.interrupt().disable();
    	//Make an instance of the waitThread class, pass the currentThread and the time we want it to wait until it wakes
    	waitThread wakeLater = new waitThread(KThread.currentThread(),wakeTime);
    	//Add it to the Priority Queue that prioritizes based on the the wakeTime
    	waitUntilQueue.add(wakeLater);
    	//Put the thread to sleep
    	KThread.currentThread().sleep();
    	//Restore Interrupts
		Machine.interrupt().restore(intStatus);
    }
    //Declare the priority queue for threads to wait for their time to wake up again
    PriorityQueue<waitThread> waitUntilQueue = new PriorityQueue<waitThread>();
	
}
