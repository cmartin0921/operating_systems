package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {

	//filelist of open file
	//private OpenFile[] fileList = new OpenFile[16];
	private OpenFile[] fileList;
	
	private int STDIN = 0;
	private int STDOUT = 1;
	
	//Process ID of the process
	int processID; //BRIAN
	//ProcessID counter
	int pidCounter = 0; //BRIAN
	//ParentID
	public int parentID;
	//Status of process
	int rootID = 0;
	public int status = -999;
	//Global User Thread variable, so we can access the thread in join.
	public UThread userT;
	//List of children for a parent
	ArrayList<UserProcess> children =new ArrayList<UserProcess>();

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		
		this.fileList = new OpenFile[16];
		//init in and out
		this.fileList[STDIN] = UserKernel.console.openForReading();
		this.fileList[STDOUT] = UserKernel.console.openForWriting();
		
		//init filelist
		//this.fileList = new OpenFile[16];
		/*for(int i = STDOUT; i < fileList.length; i++) {
			
			this.fileList[i] = null;
			
		}*/

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		//Broke up original code to store global user thread variable
		//Setting global user thread variable
		userT = new UThread(this);
		userT.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

//TASK 2//
	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) 
	{
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		// create new processor object and get the memory
		Processor machineProcessor = Machine.processor();
		byte[] machineMemory = machineProcessor.getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//   return 0;

		int virtualPageNumber = machineProcessor.pageFromAddress(vaddr);
		int memAddrOffset = machineProcessor.offsetFromAddress(vaddr);

		TranslationEntry transEntry = pageTable[virtualPageNumber];
		transEntry.used = true;

		int physicalPageNumber = transEntry.ppn;
		int physicalAddress = physicalPageNumber*pageSize + memAddrOffset;

		if (physicalPageNumber < 0 || physicalPageNumber >= machineProcessor.getNumPhysPages())
		{
			return 0;
		}

		int amount = Math.min(length, machineMemory.length-vaddr);
		System.arraycopy(machineMemory, vaddr, data, offset, amount);

		return amount;
    }
	
    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		Processor machineProcessor = Machine.processor();
		byte[] machineMemory = machineProcessor.getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//   return 0;

		int virtualPageNumber = machineProcessor.pageFromAddress(vaddr);
		int memAddrOffset = machineProcessor.offsetFromAddress(vaddr);

		TranslationEntry transEntry = pageTable[virtualPageNumber];
		transEntry.used = true;
		transEntry.dirty = true;

		int physicalPageNumber = transEntry.ppn;
		int PhysicalAddress = physicalPageNumber*pageSize + memAddrOffset;

		if (transEntry.readOnly)
		{
			return 0;
		}

		if (physicalPageNumber < 0 || physicalPageNumber >= machineProcessor.getNumPhysPages())
		{
			return 0;
		}

		int amount = Math.min(length, machineMemory.length-vaddr);
		System.arraycopy(data, offset, machineMemory, vaddr, amount);

		return amount;
    }
	
	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
		{
			int physicalPageNumber = UserKernel.removePage();
			pageTable[i] = new TranslationEntry(i, physicalPageNumber, true, false, false, false);
		}

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() 
	{
		if (numPages > Machine.processor().getNumPhysPages()) 
		{
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) 
		{
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
				  + " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) 
			{
				int vpn = section.getFirstVPN()+i;

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);

				TranslationEntry transEntry = pageTable[vpn];
				transEntry.readOnly = section.isReadOnly();
				int pysicalPageNumber = transEntry.ppn;
				section.loadPage(i, pysicalPageNumber);
			}
		}

		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() 
	{
		for (int i = 0; i < numPages; i++)
		{
			UserKernel.returnPage(pageTable[i].ppn);
			pageTable[i].valid = false;
		}
    }
//END TASK 2//    

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor machineProcessor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < machineProcessor.numUserRegisters; i++)
			machineProcessor.writeRegister(i, 0);

		// initialize PC and SP according
		machineProcessor.writeRegister(Processor.regPC, initialPC);
		machineProcessor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		machineProcessor.writeRegister(Processor.regA0, argc);
		machineProcessor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call. 
	 */
	private int handleHalt() 
	{
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int creat(int name) 
	{
		//syscall.h says char but actually passes int
		//int intname = (int)name;
		//cast as int, set default filedesciptor to out of bounds
		//int intname = (int)name;
		int fileDescriptor = -999;
		String file;
		OpenFile cfile;

		//go through list of files and find a free file descriptor
		for(int i = 0; i < this.fileList.length; i++) {

			if(this.fileList[i] == null) {
				//if free add
				fileDescriptor = i;

			}

		}

		//check if file descriptor is still out of bounds
		if(fileDescriptor < 0 || fileDescriptor > 15) {

			return -1;

		}

		//check to see if file is valid
		file = readVirtualMemoryString(name, 256);

		if(file == null) {

			return -1;

		}

		//set to true as creating a new file
		cfile = UserKernel.fileSystem.open(file, true);

		if(cfile == null) {

			return -1;

		}

		//if pass everything set the postition to the new file
		this.fileList[fileDescriptor] = cfile;
		//return the descriptor
		return fileDescriptor;

	}

	private int open(int name) {
		//sysn.h says char but actually passes int
		//cast as int, set default filedesciptor to out of bounds
		//int intname = (int)name;
		int fileDescriptor = -999;
		String file;
		OpenFile cfile;

		//go through list of files and find a free file descriptor
		for(int i = 0; i < this.fileList.length; i++) {

			if(this.fileList[i] == null) {
				//if free add
				fileDescriptor = i;

			}

		}

		//check if file descriptor is still out of bounds
		if(fileDescriptor < 0 || fileDescriptor > 15) {

			return -1;

		}

		//check to see if file is valid
		file = readVirtualMemoryString(name, 256);

		if(file == null) {

			return -1;

		}

		//set to false because are not creating a new file
		cfile = UserKernel.fileSystem.open(file, false);

		if(cfile == null) {

			return -1;

		}

		//if pass everything set the postition to the new file
		this.fileList[fileDescriptor] = cfile;
		//return the descriptor
		return fileDescriptor;

	}

	private int read(int fileDescriptor, int buffer, int count) 
	{

		//check if filedescriptor is valid
		if(fileDescriptor >= 0 && fileDescriptor < 16) {

			int read = 0;
			int temp = 0;
			byte[] buf = new byte[count];
			int offset = 0;
			int length = count;

			//check if it points to a valid file
			if(this.fileList[fileDescriptor] == null) {

				return -1;

			}

			//make sure count is valid before sending
			if(count < 0) {
			
				return -1;
				
			}
			//while current bit less than final
			while (read < count) {

				//offset is now our current bit, copy what is in that and add to the total
				offset = read;
				length = offset;
				temp = this.fileList[fileDescriptor].read(buf, offset, count - length);

				if(temp == -1) {

					return -1;

				}

				read += temp;

			}

			//check to see that the bytes read is the same
			if(writeVirtualMemory(buffer, buf, 0, read) != read) {

				return -1;

			}

			//return bytes read
			return read;

		}

		return -1;

	}

	private int write(int fileDescriptor, int buffer, int count) {

		//if fileDescriptor is valid
		if(fileDescriptor >= 0 && fileDescriptor < 16) {

			int write = 0;
			int temp;
			byte[] buf = new byte[count];
			int offset;

			//if length to write is valid
			if(count < 0) {

				return -1;

			}

			//if file is valid
			if(this.fileList[fileDescriptor] == null) {

				return -1;

			}

			//write the bits
			while(write < count) {

				offset = write;
				temp = this.fileList[fileDescriptor].write(buf, offset, count - offset);

				if(temp == -1) {

					return -1;

				}

				write += temp;

			}

			//check if its the same
			/*if(readVirtualMemory(buffer, buf, 0, write) != write) {

				return -1;

			}*/

			return write;

		}

		return -1;

	}

	private int close(int fileDescriptor) {
		//check if valid file descriptor
		if(fileDescriptor >= 0 && fileDescriptor < 16) {

			//grab the file
			OpenFile file;
			file = this.fileList[fileDescriptor];

			//check if the file grabbed is valid
			if(file == null || file.length() < 0) {
				//if not return error
				return -1;

			}

			//close the file and free descriptor
			file.close();
			
			if(file.length() != -1) {
				
				return -1;
				
			}
			
			this.fileList[fileDescriptor] = null;

			return 0;

		}

		return -1;

	}

	private int unlink(int name) {
		//sys.h has char but is actually int passed
		//grab char name cast int
		//int intname = (int)name;
		String file;
		//grab file
		file = readVirtualMemoryString(name, 256);

		if(file == null) {
			//test to see if file exists if not return error
			return -1;

		}

		//remove
		if (UserKernel.fileSystem.remove(file) == false) {
			//if remove fail return error
			return -1;

		}
		//return pass
		return 0;
	}

	void exit(int status) {
		//Close all open files
		for(int i = 0; i< this.fileList.length;i++) {
			if(this.fileList[i] != null) {
				//this.fileList[i].close();
				this.close(i);
			}
		}
		//Free their memory
		this.unloadSections();
		//If the process has any children, make it so they aren't related anymore
		while(this.children!= null && !this.children.isEmpty()) {
			//Reference to the child process
			UserProcess orphan = this.children.get(0);
			//Remove from list of children
			this.children.remove(0);
			//Disown the child
			orphan.parentID = rootID;
		}
		//Set status to exited
		this.status = status;
		//If the parentID is the root then terminate the kernel
		if(this.parentID == rootID) {
			Kernel.kernel.terminate();
		}else {
			//Else just finish this thread
			UThread.finish();
		}

	}
	private int execute(int name, int argc, int argv) {
		//Check if an argument/file was passed
		if(argc < 1) {
			return -1;
		}
		//Check if the file can be found
		String file = readVirtualMemoryString(name,256);
		if(file == null) {
			return -1;
		}
		//Check if its a .coff extension
		if(!file.substring(file.length()-4).equals("coff")) {
			return -1;
		}

		//Array of arguments/files
		String[] files = new String[argc];
		//Buffer
		byte[] addrBuff = new byte[4];
		for(int i = 0;i < argc;i++) {
			//Check if bytes transferred is 4
			int check = readVirtualMemory(argv+i*4,addrBuff);
			if(check != 4) {
				return -1;
			}
			//Address of the file
			int addr = Lib.bytesToInt(addrBuff,0);
			//Check if the string read exists
			String temp = readVirtualMemoryString(addr,256);
			if(temp == null) {
				return -1;
			}
			files[i] = temp;
		}
		//Create child process
		UserProcess newBorn = UserProcess.newUserProcess();
		//Make child parent id equal to this process id
		newBorn.parentID = this.processID;
		//Add to the list of children
		this.children.add(newBorn);
		//Check if child successfully runs
		boolean success = newBorn.execute(file, files);
		if(success) {
			//if yes return the id of this
			return newBorn.parentID;
		}else {
			//error
			return -1;
		}

	}
	
	private int join(int processID,int statusAdr) {
		//ProcessID can't be negative
		if(processID < 0) {
			return -1;
		}
		//Addr can't be negative
		if(statusAdr<0) {
			return -1;
		}
		//Store the child being joined
		//Set as null to check later
		UserProcess orphan = null;
		//Search for child
		for(int i = 0;i < this.children.size();i++) {
			if(this.children.get(i).processID == processID) {
				orphan = this.children.get(i);
				this.children.remove(i);
				break;
			}
		}
		//Child not found
		if(orphan == null) {
			//ProcessID does not refer to child of Process
			return -1;
		}
		//Join the child
		orphan.userT.join();
		//Check if the status of child passed is null or not
		if(orphan.status == -999) {
			//Unhandled Exception
			return 0;
		}
		//Buffer
		byte[] addrBuff = new byte[4];
		addrBuff = Lib.bytesFromInt(orphan.status);
		//Check if bytes tranferred correctly
		int check = writeVirtualMemory(statusAdr,addrBuff);
		if(check != 4) {
			//Unhandled Exception
			return 0;
		}else {
			//Exited normally
			return 1;
		}
	}
	
	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 * 
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallCreate:
			return creat(a0);
		case syscallOpen:
			return open(a0);
		case syscallRead:
			return read(a0, a1, a2);
		case syscallWrite:
			return write(a0, a1, a2);
		case syscallClose:
			return close(a0);
		case syscallUnlink:
			return unlink(a0);
		case syscallExit:
			exit(a0);
		case syscallExec:
			return execute(a0, a1, a2);
		case syscallJoin:
			return join(a0, a1);


		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3)
					);
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;				       

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
}