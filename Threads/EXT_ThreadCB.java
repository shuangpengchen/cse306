/*
Author: Shuangpeng chen 
ID: 110143903
Pledge: I pledge my honor that all parts of this project were done by me individually, without collaboration with anyone, and without consulting external sources that help with similar projects.
*/

package osp.Threads;

public class EXT_ThreadCB extends ThreadCB
// implements Comparable
{
	int total_waiting_time;
	
	public EXT_ThreadCB()
    	{
        	super();
        	this.total_waiting_time=0;
    	}

	// update the priority and return if the priority is positive or not
	public boolean update_priority()
	{
		double CT = getCumulativeTaskCPUTime(this.getTask().getID());
		if(CT==-1.0){
    		System.out.println("task does not exist ....");
    		System.exit(1);
		}
		int prio = (int)(1.5*this.total_waiting_time - this.getTimeOnCPU() - 0.3*CT);
		this.setPriority(prio);
		if(this.getPriority()>0)
			return true;
		else 
			return false;
	}
}
