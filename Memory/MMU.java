/*
@pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
@name: shuangpeng chen
@ID: 110143903
*/



package osp.Memory;
import java.util.ArrayList;
import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;






/**
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
*/
public class MMU extends IflMMU
{

  public static ArrayList<FrameTableEntry> LRU_list;               // this  list is maintain to keep the LRU frame at the first of this list, when one frame got referenced, it will be moved to the end of list

    /** 
        This method is called once before the simulation starts. 
	Can be used to initialize the frame table and other static variables.

        @OSPProject Memory
    */
    public static void init()
    {
      LRU_list = new ArrayList();                      // init LRU-list
      for(int i=0;i<getFrameTableSize();i++){           // init frame table
        FrameTableEntry temp = new FrameTableEntry(i);
        setFrame(i, temp);
        LRU_list.add(temp);                             // append it to the end of LRU_list
      }
      Daemon.create("Swap-out daemon",new SwapDaemon(),20000);              // init daemon
    }

    /**
    @kg?? : return value might be null!!!!! ok?

       This method handlies memory references. The method must 
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault 
       by making an interrupt if the page is invalid, finally, 
       if the page is still valid, i.e., not swapped out by another 
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue, 
       and it is possible that some other thread will take away the frame.)
       
       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform 
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
    */
    static public PageTableEntry do_refer(int memoryAddress,
					  int referenceType, ThreadCB thread)
    {
      int offsetBits= getVirtualAddressBits() - getPageAddressBits();
      int pagesize = (int)Math.pow(2,offsetBits);
      PageTableEntry theone=null;
      theone = thread.getTask().getPageTable().pages[memoryAddress/pagesize];
      if(theone.isValid()){                                                                         // case when page is valid
        theone.getFrame().setReferenced(true);                                            //set the referenced bit to be true
        if(referenceType == MemoryWrite)                                                  // case when the referencedtype is memorywrite, the dirty bit should be set 
          theone.getFrame().setDirty(true);
        MMU.append(theone.getFrame());
        return theone;
      }else if(theone.getValidatingThread()!=null){                                                              // case when page is  invalid
        thread.suspend(theone);                                                       // suspend the thread, waiting for the page to become valid
        if(thread.getStatus()!=ThreadCB.ThreadKill && theone.isValid())                    // case when thread got killed after PFH
        {
          theone.getFrame().setReferenced(true);
          if(referenceType == MemoryWrite)
            theone.getFrame().setDirty(true);
          MMU.append(theone.getFrame());
        }
        return theone;
      }else{                                                        // case when the getValidatingThread() return null
        InterruptVector.setPage(theone);                            // set the faulty page
        InterruptVector.setReferenceType(referenceType);            //  set the reference type
        InterruptVector.setThread(thread);                          // set the thread cause PF
        CPU.interrupt(PageFault);                                   
        if(thread.getStatus()!=ThreadCB.ThreadKill && theone.isValid())                   
        {
          theone.getFrame().setReferenced(true);
          if(referenceType == MemoryWrite)
            theone.getFrame().setDirty(true);
          MMU.append(theone.getFrame());
        }
        return theone;
      }
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
     
	@OSPProject Memory
     */
    public static void atError()
    {
        // your code goes here
    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
      @OSPProject Memory
     */
    public static void atWarning()
    {
        // your code goes here
    }

/*
  this method return the LRU frmae from the LRU list. So, my implementation of LRU does not require a time stamp, the order of LRU_list does the same thing.
*/
    public synchronized static FrameTableEntry getLRUFrame(){      
      boolean allUnavailable =true;
      for(int i=0;i<LRU_list.size();i++){
        if(!LRU_list.get(i).isReserved() && LRU_list.get(i).getLockCount()==0){
          allUnavailable=false;
          FrameTableEntry rt = LRU_list.get(i);
          LRU_list.remove(rt);
          LRU_list.add(rt);
          return rt;
        }
      }
      return null;
     }
/*
  this is the method implementing LRU,
  when one thread is referenced, this frame will be remove from the LRU list, and added to the end, meaning the most resent referenced frame
  The LRU will always at the head of LRU list
  @purpose, move the referenced to the end of LRU_list
*/
     public static void append(FrameTableEntry frame){
      LRU_list.remove(frame);
      LRU_list.add(frame);
     }



// function/s of testing purpose
     public static void printLRU_list(){
      Arrays.toString(LRU_list.toArray());
     }





}

/*
      Feel free to add local classes to improve the readability of your code
*/
