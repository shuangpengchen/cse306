/*
@pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
@name: shuangpeng chen
@ID: 110143903
*/




package osp.Memory;
import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler
{


    /**
        This method handles a page fault. 

        It must check and return if the page is valid, 

        It must check if the page is already being brought in by some other
	thread, i.e., if the page's has already pagefaulted
	(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.
        
        If none of the above is true, a new frame must be chosen 
        and reserved until the swap in of the requested 
        page into this frame is complete. 

	Note that you have to make sure that the validating thread of
	a page is set correctly. To this end, you must set the page's
	validating thread using setValidatingThread() when a pagefault
	happens and you must set it back to null when the pagefault is over.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissasociated 
        from the frame and marked invalid. After the swap-in, the 
        frame must be marked clean. The swap-ins and swap-outs 
        must are preformed using regular calls read() and write().

        The student implementation should define additional methods, e.g, 
        a method to search for an available frame.

	Note: multiple threads might be waiting for completion of the
	page fault. The thread that initiated the pagefault would be
	waiting on the IORBs that are tasked to bring the page in (and
	to free the frame during the swapout). However, while
	pagefault is in progress, other threads might request the same
	page. Those threads won't cause another pagefault, of course,
	but they would enqueue themselves on the page (a page is also
	an Event!), waiting for the completion of the original
	pagefault. It is thus important to call notifyThreads() on the
	page at the end -- regardless of whether the pagefault
	succeeded in bringing the page in or not.

        @param thread the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page the memory page 

	@return SUCCESS is everything is fine; FAILURE if the thread
	dies while waiting for swap in or swap out or if the page is
	already in memory and no page fault was necessary (well, this
	shouldn't happen, but...). In addition, if there is no frame
	that can be allocated to satisfy the page fault, then it
	should return NotEnoughMemory

        @OSPProject Memory
    */
    public static int do_handlePageFault(ThreadCB thread, 
					 int referenceType,
					 PageTableEntry page)
    {
        page.setValidatingThread(thread);                                               // set the validating thread at the beginning of page fualt handler
        if(page.isValid()){                                                             // caes when page is valid
            page.notifyThreads();
            page.setValidatingThread(null);                                                  // unset the validating thread before return
            ThreadCB.dispatch();
            return FAILURE;
        }
        boolean notenoughmemory = true;
        for(int i=0;i<MMU.getFrameTableSize();i++){                                         // searching for unreserved or unlocked frame
            if(!MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount()==0){         //case when free frame is found
                notenoughmemory=false;
                break;
            }
        }
        if(notenoughmemory==true){                                                      // case when not enough memory exist
            page.notifyThreads();
            page.setValidatingThread(null);                                                  // unset the validating thread before return
            ThreadCB.dispatch();
            return NotEnoughMemory;                                 
        }  
        SystemEvent sysEvent = new SystemEvent("pfEvent");                                       // creating system event object
        thread.suspend(sysEvent);                                                       // suspend the page fault thread
        FrameTableEntry theselectedFrame = null;                                                
        theselectedFrame=MMU.getLRUFrame();                                         // selected LRU frame from my method
        theselectedFrame.setReserved(thread.getTask());                                 // reserved the frame protecting it from being taken away
        MMU.append(theselectedFrame);
        if(theselectedFrame.getPage() == null){                                         // case 1:  frame is free
            page.setFrame(theselectedFrame);                                            // assign page to frame   
            //swap-in
            OpenFile swap_file = thread.getTask().getSwapFile();
            swap_file.read(page.getID(),page,thread);
            //  checking status of thread;
            if(thread.getStatus() == ThreadKill){                                         //case when thread goes killed after swap-in
                page.setFrame(null);                                     
                page.notifyThreads();                                                       
                //sysEvent.notifyThreads();                                                       // resume the PF thread
                page.setValidatingThread(null);                                                  // unset the validating thread before return
                ThreadCB.dispatch();
                return FAILURE;
            }
            theselectedFrame.setPage(page);
            page.setValid(true);                                                                //– set the P ’s validity flag correctly
            if(referenceType == MemoryWrite){
                theselectedFrame.setDirty(true);                        
            }
            theselectedFrame.setUnreserved(thread.getTask());                                // unreserved the frame
            page.setValidatingThread(null);                                                      // unset the validating thread before return
            page.notifyThreads();                                                           // notify threads waiting for faulty page                                                      
            sysEvent.notifyThreads();                                                       // resume the PF thread
            ThreadCB.dispatch();                                                            // dispatch a new thread to run
            return SUCCESS;
        }
        else if(!theselectedFrame.isDirty()){                                           // case 2: frame contains clean pag         
            // freeing frame 
            PageTableEntry temp=theselectedFrame.getPage();
            theselectedFrame.setPage(null);
            theselectedFrame.setDirty(false);
            theselectedFrame.setReferenced(false);    
            temp.setValid(false);
            temp.setFrame(null);
            page.setFrame(theselectedFrame);                                            // assign page to frame   
            //swap-in
            OpenFile swap_file = thread.getTask().getSwapFile();
            swap_file.read(page.getID(),page,thread);
             //  checking status of thread;
            if(thread.getStatus() == ThreadKill){                                         //case when thread goes killed after swap-in
                page.setFrame(null);                                     
                page.notifyThreads();                                                       
                //sysEvent.notifyThreads();                                                       // resume the PF thread
                page.setValidatingThread(null);                                                  // unset the validating thread before return
                ThreadCB.dispatch();
                return FAILURE;
            }
            theselectedFrame.setPage(page);
            page.setValid(true);                                                        //– set the P ’s validity flag correctly
            if(referenceType == MemoryWrite){
                theselectedFrame.setDirty(true);                        
            }
            theselectedFrame.setUnreserved(thread.getTask());                                // unreserved the frame
            page.setValidatingThread(null);                                                      // unset the validating thread before retur
            page.notifyThreads();                                                           // notify threads waiting for faulty page                                                      
            sysEvent.notifyThreads();                                                       // resume the PF thread            
            ThreadCB.dispatch();                                                            // dispatch a new thread to run
            return SUCCESS;
        }
        else if(theselectedFrame.isDirty()){                                            //case 3: contains dirty page
            PageTableEntry swap_out_page = theselectedFrame.getPage();                         // the page that own the selected frame previously
            OpenFile swap_file = swap_out_page.getTask().getSwapFile();                        // get the swap file from frame.page.task.file()
            swap_file.write(swap_out_page.getID(),swap_out_page,thread);                // performing swap out
            if(thread.getStatus() == ThreadKill){                                         //case when thread goes killed after swap-in
                page.notifyThreads();                                                       
                //sysEvent.notifyThreads();                                                       // resume the PF thread
                page.setValidatingThread(null);                                                  // unset the validating thread before return
                ThreadCB.dispatch();
                return FAILURE;
            }
            // freeing frame 
            theselectedFrame.setPage(null);  
            theselectedFrame.setDirty(false);
            theselectedFrame.setReferenced(false);
            swap_out_page.setValid(false);
            swap_out_page.setFrame(null);                                                   // invaliding page
            page.setFrame(theselectedFrame);                                            // assign page to frame    
            //swap-in
            swap_file = thread.getTask().getSwapFile();
            swap_file.read(page.getID(),page,thread);
            //check for status of thread
            if(thread.getStatus() == ThreadKill){                                         //case when thread goes killed after swap-in
                page.setFrame(null);                                     
                //sysEvent.notifyThreads();                                                       // resume the PF thread
                page.notifyThreads();                                                       
                page.setValidatingThread(null);                                                  // unset the validating thread before return
                ThreadCB.dispatch();
                return FAILURE;
            }
            theselectedFrame.setPage(page);
            page.setValid(true);                                                            //– set the P ’s validity flag correctly
            if(referenceType == MemoryWrite){
                theselectedFrame.setDirty(true);                        
            }
            theselectedFrame.setUnreserved(thread.getTask());                                // unreserved the frame
            page.setValidatingThread(null);                                                      // unset the validating thread before return
            page.notifyThreads();                                                           // notify threads waiting for faulty page                                                      
            sysEvent.notifyThreads();                                                       // resume the PF thread
            ThreadCB.dispatch();                                                            // dispatch a new thread to run
            return SUCCESS;
        }
        else{
            System.out.println("error case :");
            return FAILURE;
        }       
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
