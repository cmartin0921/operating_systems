package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

	private boolean isReady;
	//keeps track of if a word is ready
	private int hold, speaker, listener = 0;
	static int testa = 999;
	//holder for word,  num speakers, num listeners,

	private Lock newLock;
	private Condition2 speakerR;
	private Condition2 listenerR;
	private Condition2 inProg;
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {

		this.isReady = false;
		this.hold = 0;
		this.speaker = 0;
		this.listener = 0;
		//init values

		this.newLock = new Lock();
		//new lock
		this.speakerR = new Condition2(newLock);
		//condition variable for speakers
		this.listenerR = new Condition2(newLock);
		//condition variable for listener
		this.inProg = new Condition2(newLock);
		//condition variable for inprog message

	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param	word	the integer to transfer.
	 */
	public void speak(int word) {

		newLock.acquire();
		speaker++;
		//grab lock and incriment num speakers

		while(listener == 0 || isReady) {
			listenerR.wake();
			speakerR.sleep();
			//wake my listener and sleep
		}

		isReady = true;
		this.hold = word;
		listenerR.wakeAll();
		//if I have word wake listeners, my word is word

		while(isReady) {
			inProg.sleep();
			//sleep while inprog is active then when listenr gets word isready becomes false inprog becomes active
		}

		speaker--;
		newLock.release();
		//sent message, decrement and release lock
		/**
		 * Wait for a thread to speak through this communicator, and then return
		 * the <i>word</i> that thread passed to <tt>speak()</tt>.
		 *
		 * @return	the integer transferred.
		 */    
	}
	public int listen() {

		newLock.acquire();
		listener++;
		int gotMessage;
		//grab lock, increment num listeners, create holder for the word

		while(speaker == 0 || isReady == false) {

			speakerR.wakeAll();
			listenerR.sleep();
			//if no word ready wake up a speaker and seep
		}

		gotMessage = this.hold;
		testa = this.hold;
		isReady = false;
		//grab the message then set word as notready anymore
		listener--;
		inProg.wakeAll();
		//wake up the speaker
		newLock.release();
		//decrement listeners and rlease lock

		return gotMessage;
		//return the word we got
	}

	private static class Speaker implements Runnable{

		private int testWord = 0;
		private Communicator com;

		Speaker(Communicator comm, int value){
			this.com = comm;
			this.testWord = value;
		}

		public void run() {
			com.speak(this.testWord);
		}

		//Build testable speaker

	}

	private static class Listener implements Runnable{

		private Communicator com;

		Listener(Communicator comm){
			this.com = comm;
		}

		public void run() {
			testa = com.listen();
		}

		//Build testable listener
	}

	public static void selfTest() {
		//where to run selftest	

		System.out.println("Got to begin test");

		System.out.println("Test 1 speaker 1 listener");
		Communicator comm = new Communicator();
		KThread threadSpeaker = new KThread(new Speaker(comm, 100));
		threadSpeaker.setName("speaker").fork();
		KThread.yield();
		System.out.println("Built speaker");
		KThread threadListener = new KThread(new Listener(comm));
		threadListener.setName("Thread listener").fork();
		KThread.yield();
		System.out.println("Built listener");

		threadListener.join();
		threadSpeaker.join();
		System.out.println("Did the join");

		System.out.println(testa);
		//testing for 1 speaker 1 listener

		System.out.println("Test many speaker many listener");
		Communicator comm1 = new Communicator();

		KThread speaklist[] = new KThread[10];
		for(int i = 0; i < 10; i++) {
			speaklist[i] = new KThread(new Speaker(comm1, i));
			speaklist[i].setName("Speaker " + i).fork();
			//System.out.println(i);
		}
		KThread.yield();
		System.out.println("Built speaker list");

		KThread listenlist[] = new KThread[10];
		for(int i = 0; i < 10; i++) {
			listenlist[i] = new KThread(new Listener(comm1));
			listenlist[i].setName("Listener " + i).fork();
		}
		KThread.yield();
		System.out.println("Built listener list");

		for(int i = 0; i < 10; i++) {
			speaklist[i].join();		
			listenlist[i].join();
			System.out.println("joined lists");
			System.out.println(testa);
		}
		//test many speaker many listener
		
		System.out.println("Test many speaker 1 listener");
		Communicator comm2 = new Communicator();
		
		KThread speaklist1[] = new KThread[10];
		for(int i = 0; i < 10; i++) {
			speaklist[i] = new KThread(new Speaker(comm2, i + 1));
			speaklist[i].setName("Speaker " + i).fork();

		}
		KThread.yield();
		System.out.println("Built speaker list 2");
		
		KThread singleListener = new KThread(new Listener(comm2));
		singleListener.setName("Listener").fork();
		KThread.yield();
		System.out.println("Built listener");
		
		speaklist1[0].join();
		singleListener.join();
		System.out.println("joined one on one");
		System.out.println(testa);
		
		for(int i = 1; i < 10; i++) {
			speaklist[i].join();
			singleListener.join();
			System.out.println("joined on " + i);
			System.out.println(testa);
		}
		//test many speaker 1 listener
		
		
		System.out.println("Test one speaker many listener");
		Communicator comm3 = new Communicator();
		
		KThread singlespeaker = new KThread(new Speaker(comm3, 1));
		singlespeaker.setName("Speaker").fork();
		KThread.yield();
		System.out.println("Built speaker");
		
		KThread listenlist1[] = new KThread[10];
		for(int i = 0; i < 10; i++) {
			listenlist1[i] = new KThread(new Listener(comm3));
			listenlist1[i].setName("Listener " + i).fork();
		}
		KThread.yield();
		System.out.println("Built listener list");	
		
		singlespeaker.join();
		listenlist1[0].join();
		System.out.println("joined");
		System.out.println(testa);
		
		for(int i = 1; i < 10; i++) {
			singlespeaker.join();
			listenlist1[i].join();
			System.out.println("joined on " + i);
			System.out.println(testa);
		}
		//test one speaker many listener
		
	}
}
