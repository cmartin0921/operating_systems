package nachos.threads;
//Implements comparable because we want to use a priority queue to judge priority
public class waitThread implements Comparable<waitThread>{
	//Member variables
	private long timetoWake; //time until it wakes up
	private KThread thread; //The thread itself
	waitThread(KThread currThread,long wakeTime){
		//Set equal to the arguments
		thread = currThread; 
		timetoWake = wakeTime;

	}
	//The compare function written following the KThread compareTo for comparisons
	public int compareTo(waitThread wait) {
		if(this.timetoWake > wait.getTime()) {
			//Bigger means further back in the queue
			return 1;
		}else if(this.timetoWake < wait.getTime()) {
			//Smaller means closer to the front
			return -1;
		}else {
			//If they have equal wait times, compare by their ids ie the smaller the id the older it is
			//So we want it to run first
			//Could have also returned 0 if we didn't care
			return thread.compareTo(wait.getThread());
		}
		
	}
	KThread getThread() {
		return thread;
	}
	long getTime() {
		return timetoWake;
	}

}