/*
@pledge: I pledge my honor that all parts of this project were done by me individ- ually, without collaboration with anyone, and without consulting external sources that help with similar projects.
@name: shuangpeng chen
@ID: 110143903
*/



package osp.Memory;

import osp.Threads.*;
import osp.IFLModules.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.FileSys.OpenFile;



/*
 daemon swap out page periodically 
 	1. should be register in the init() function of main classes of project
 	2. USAGE : 			Daemon.create("My own daemon", new MyDaemon(), 20000);
*/
class SwapDaemon implements DaemonInterface 
{
	
	public void unleash(ThreadCB thread) 
	{
		for(int i=0;i<MMU.getFrameTableSize();i++){																	
        	if(MMU.getFrame(i).isDirty() && !MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount()==0){	// only swap-out free frame , otherwise, errors will happened
        			PageTableEntry page = MMU.getFrame(i).getPage();
          			OpenFile swap_file = page.getTask().getSwapFile();
          			if(page!=null){
          				swap_file.write(page.getID(),page,thread);
          			}
        		}
          		
      		}
        }
	}
