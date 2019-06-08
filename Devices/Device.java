/*
PROJECT-3
Due : April 25th
Name: Shuangpeng Chen
ID: 110143903
pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
*/



package osp.Devices;
import java.util.Enumeration;


/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

public class Device extends IflDevice
{
    
    private GenericList current_processing_Q;         //pointer : point to the current_processing_Q, which it the IORB selected from
    private GenericList firstQ;                       // first Q
    private GenericList secondQ;                      //second Q

    /**
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
    public Device(int id, int numberOfBlocks)
    {

      // init Queues and device
      super(id,numberOfBlocks);             
      iorbQueue = new GenericList();
      firstQ=new GenericList();
      secondQ=new GenericList();
      current_processing_Q=firstQ;
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init()
    {
       //no code here
    }

    /**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
    public int do_enqueueIORB(IORB iorb)
    {

        iorb.getPage().lock(iorb);                          // first lock the page
        iorb.getOpenFile().incrementIORBCount();        // increment iorb count of open file
        iorb.setCylinder(calculateCylinder(iorb.getBlockNumber()));  //set the cylinder of iorb
        if(iorb.getThread().getStatus() == ThreadKill){   //check status of thread
          return FAILURE;
        }
        if(!isBusy()){                                     //check status of device
          startIO(iorb);                  //start io
          return SUCCESS;
        }else{                          //case : busy
          ((GenericList) iorbQueue).append(iorb); //add the iorb to the main Q
          if(current_processing_Q == firstQ){     // add the iorb to the non-current_processing_Q
            secondQ.append(iorb);
          }else{
            firstQ.append(iorb);
          }
          return SUCCESS;
        }

    }

    /**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB()
    {
      if(iorbQueue.isEmpty()){      // since the iorbQueue is the main_Q, and it holds all the IORBs, if it's empty, meaning there is not IORB waiting to be processed
        return null;
      }
      if(current_processing_Q.isEmpty()){       // when the current_processing_Q is empty; this is the time to switch from one Q to another Q;
        current_processing_Q = (current_processing_Q==firstQ?secondQ:firstQ);     // switching
      }
      IORB rt = SSTF_IORB();      // selected the IORB based on SSTF method
      ((GenericList) iorbQueue).remove(rt);     // remove such IORB from main_Q
      return rt;
    }

    /**
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
    public void do_cancelPendingIO(ThreadCB thread)
    {
      if(!iorbQueue.isEmpty()){                         // if main_Q is empty, not IORBs at all, then no need to remove the pending IORB of a killed thread
        Enumeration enumeration = ((GenericList) iorbQueue).forwardIterator();          // get the iterator
        while(enumeration.hasMoreElements()){         //loop through main_Q
          IORB temp = (IORB) enumeration.nextElement();       
          if(temp.getThread() == thread){                 // case : such IORB of a killed thread is found
            temp.getPage().unlock();                  // unlock page
            temp.getOpenFile().decrementIORBCount();      // decrement iorb's openfile' count
          if(temp.getOpenFile().closePending==true && temp.getOpenFile().getIORBCount()==0){        // case: openfile need to be closed
            temp.getOpenFile().close();
          }
            ((GenericList) iorbQueue).remove(temp);       //remove from main_Q
            firstQ.remove(temp);                          //try to remove from firstQ
            secondQ.remove(temp);                 //try to remove from secondQ
          }
        }
      }
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atWarning()
    {
        // your code goes here

    }


/*
this method calcualte the cylinder based on the input blockNumber
*/

    private int calculateCylinder(int blockNumber){
      int offsetBits = MMU.getVirtualAddressBits() - MMU.getPageAddressBits();
      int blockSize = (int)Math.pow(2,offsetBits);    // this is the page size either
      int sectorSize = ((Disk) this).getBytesPerSector();
      int getPlatters = ((Disk) this).getPlatters();
      int getTracksPerPlatter = ((Disk) this).getTracksPerPlatter();
      int getSectorsPerTrack = ((Disk) this).getSectorsPerTrack();
      int sectorNumberPerBlock=blockSize/sectorSize;
      int blockNumberPerTrack = getSectorsPerTrack/sectorNumberPerBlock;
      int whichTrack=(blockNumber/blockNumberPerTrack)/getPlatters;
      return whichTrack;
    }



/*
this method find the shortest seeking time from the current head position. and selected that IORB to process
*/
    private IORB SSTF_IORB(){
        Enumeration enumeration = current_processing_Q.forwardIterator();     //get iterator
      IORB rt=null;
      int position = ((Disk) this).getHeadPosition();         // head position
      while(enumeration.hasMoreElements()){     // loop through current_processing_Q
          if(rt==null){
            rt=(IORB)enumeration.nextElement();
          }else{
            IORB temp =(IORB) enumeration.nextElement();
            rt=  Math.abs(rt.getCylinder()-position) > Math.abs(temp.getCylinder()-position) ? temp:rt; //update  rt variable if new min seek IORB is found
          }
      }
      current_processing_Q.remove(rt);      //remove from the current_processing_Q
      return rt;
    }





    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
