/*
PROJECT-3
Due : April 25th
Name: Shuangpeng Chen
ID: 110143903
pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
*/


package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
        // geting the basic info about interrupt : including which IORB, the openfile of such IORB, and the thread, and page
        IORB iorb =(IORB) InterruptVector.getEvent();
        OpenFile openfile = iorb.getOpenFile();
        ThreadCB thread = iorb.getThread();
        PageTableEntry page = iorb.getPage();
        //decrement the count of openfile 
        openfile.decrementIORBCount();
        // check whether the openfile should be cloce or not
        if(openfile.closePending==true && openfile.getIORBCount()==0){
            openfile.close();
        }
        // unlock the page
        page.unlock();
        // case: such I/O request is not Swap-in or swap-out, also the task is still alive
        if(iorb.getDeviceID()!=SwapDeviceID && thread.getTask().getStatus() == TaskLive){      
            if(thread.getStatus() != ThreadKill)
                page.getFrame().setReferenced(true);        // set the referenced bit
            if(iorb.getIOType() == FileRead)
                page.getFrame().setDirty(true);             // set the dirty bit
        }else if(iorb.getDeviceID()==SwapDeviceID && thread.getTask().getStatus() == TaskLive){         // case: such I/O request is swap-in or swap-out
            //If the I/O was directed to the swap device ..... (meaning swap-out only, or also including swap-in)
            page.getFrame().setDirty(false);                // set the dirty bit
        }else if(thread.getTask().getStatus() == TaskTerm){     // case : task is terminated
            if(page.getFrame().isReserved())                    
                page.getFrame().setUnreserved(thread.getTask());    // unreserved the frame
        }else{
            System.out.println("unknown case");                     // should not goes here : undefined case
        }

        // notify all threads waiting for this I/O request
        iorb.notifyThreads();
        Device device = Device.get(iorb.getDeviceID());
        // set device to idle
        device.setBusy(false);                                  
        IORB temp;
        if((temp=device.dequeueIORB())!=null){      //start another IORB
            device.startIO(temp);
        }
        //dispatch another thread
        ThreadCB.dispatch();
    }

    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
