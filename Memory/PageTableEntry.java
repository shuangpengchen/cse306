/*
@pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
@name: shuangpeng chen
@ID: 110143903
*/



package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;
/**
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.
   
   @OSPProject Memory

*/

public class PageTableEntry extends IflPageTableEntry
{
    /**
       The constructor. Must call

       	   super(ownerPageTable,pageNumber);
	   
       as its first statement.

       @OSPProject Memory
    */
    public PageTableEntry(PageTable ownerPageTable, int pageNumber)
    {
        super(ownerPageTable,pageNumber);
    }

    /**
       This method increases the lock count on the page by one. 

	The method must FIRST increment lockCount, THEN  
	check if the page is valid, and if it is not and no 
	page validation event is present for the page, start page fault 
	by calling PageFaultHandler.handlePageFault().

	@return SUCCESS or FAILURE
	FAILURE happens when the pagefault due to locking fails or the 
	that created the IORB thread gets killed.

	@OSPProject Memory
     */
    public int do_lock(IORB iorb)
    {
      if(this.isValid()){                                                     //case when page is valid         
        this.getFrame().incrementLockCount();
        MMU.append(this.getFrame());
        return SUCCESS;
      }else{                                                                  // case when page is invalid : pagefault_handler should be called
        if(this.getValidatingThread()==null){                                 // case when page fault for this page never trigger before
          if(PageFaultHandler.handlePageFault(iorb.getThread(), MemoryLock,this)==SUCCESS && this.isValid()){       // case when page become valid
            if(iorb.getThread().getStatus() == ThreadCB.ThreadKill){          // case when thread got killed during PFH
              return FAILURE;
            }
            this.getFrame().incrementLockCount();
            MMU.append(this.getFrame());
            return SUCCESS;
          }else{                                                            // case when PFH return failure
            return FAILURE;
          }
        }
        else if(this.getValidatingThread() == iorb.getThread()){              // case when Thread1 == thread2
          this.getFrame().incrementLockCount();
          MMU.append(this.getFrame());
          return SUCCESS;
        }else{                                                                // case when thread1 != thread2
          iorb.getThread().suspend(this);                                     //supsend the thread2
          if(this.isValid()){                                                 // case when page become valid after PFH
            if(iorb.getThread().getStatus() == ThreadCB.ThreadKill){          // case when thread got killed during PFH
              return FAILURE;
            }
            this.getFrame().incrementLockCount();
            MMU.append(this.getFrame());
            return SUCCESS;
          }else{                                                              // case when page become invalid after PFH
            return FAILURE;
          }
        }
      }
    }

    /** This method decreases the lock count on the page by one. 
	This method must decrement lockCount, but not below zero.
	@OSPProject Memory
    */
    public void do_unlock()
    {
      FrameTableEntry temp = getFrame();
      if(temp.getLockCount()>0){
        temp.decrementLockCount();                                        //decrease the lock count
      }
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
