/*
@pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
@name: shuangpeng chen
@ID: 110143903
*/


package osp.Memory;
/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
*/
import java.lang.Math;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;

public class PageTable extends IflPageTable
{
    /** 
	The page table constructor. Must call
	
	    super(ownerTask)

	as its first statement.

	@OSPProject Memory
    
    */
    public PageTable(TaskCB ownerTask)
    {
      super(ownerTask);
      int max = (int)Math.pow(2, MMU.getPageAddressBits());                // calculate the max size of a page table
      pages = new PageTableEntry[max];                                    // init page table
      for(int i=0;i<max;i++){                                           
        pages[i]= new PageTableEntry(this,i);                         // init page table entries
      }
    }

    /**

       Frees up main memory occupied by the task.
       Then unreserves the freed pages, if necessary.

       @OSPProject Memory
    */

    public void do_deallocateMemory()
    {
      for(int i=0;i<pages.length;i++){                                    // loop through page table and try to reset all frames
        if(pages[i].getFrame()!=null){
          pages[i].getFrame().setPage(null);                        //freeing frame
          pages[i].getFrame().setDirty(false);
          pages[i].getFrame().setReferenced(false);
          pages[i].getFrame().setPage(null);
          pages[i].setValid(false);               //invaliding page
        }
      }
        for(int i=0;i<MMU.getFrameTableSize();i++){                                       // loop through frame table and try to unreserved all frame occupied by this current task
        FrameTableEntry temp = MMU.getFrame(i);
        if(temp.getReserved() == getTask())          //case when frame is reserved and it is the current task that reserved this frame
        {
          temp.setUnreserved(getTask());
        }
      }
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */


}

/*
      Feel free to add local classes to improve the readability of your code
*/
