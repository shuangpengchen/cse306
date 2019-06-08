/*
Author: Shuangpeng chen 
ID: 110143903
Pledge: I pledge my honor that all parts of this project were done by me individually, without collaboration with anyone, and without consulting external sources that help with similar projects.
*/


package osp.Threads;
import java.util.Vector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;
import java.util.Collections;


/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB  implements Comparable
{

    static ArrayList<EXT_ThreadCB> pos_Q;      // positive priority Q
    static ArrayList<EXT_ThreadCB> neg_Q;      // negative priority Q
    static ArrayList<EXT_ThreadCB> first_2_go; //first priority Q
    static HashMap<Integer,Double> cumulativeTaskCPUTime;

    
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        super();

    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       @kg:If you find no use for this feature, leave the body of the method empty.
       @OSPProject Threads
    */
    public static void init()
    {
        pos_Q = new ArrayList();
        neg_Q = new ArrayList();
        first_2_go = new ArrayList();
        cumulativeTaskCPUTime = new HashMap();
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.


    @kg:Therefore, regardless of whether the new thread was created successfully, the dispatcher must be called
	
    @return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        //add the task to the list, 
        if(!cumulativeTaskCPUTime.containsKey(task.getID()));
            cumulativeTaskCPUTime.put(task.getID(),0.0);
        
        if(task.getThreadCount()==MaxThreadsPerTask){
            dispatch();
            return null;
        }
        EXT_ThreadCB rt=new EXT_ThreadCB();
        if(task.addThread(rt)==FAILURE){
            dispatch();
            return null;
        }
        rt.setTask(task);

        int priority = (int)(0 - 0.3*getCumulativeTaskCPUTime(task.getID()));
        rt.setPriority(priority);
        rt.setStatus(ThreadReady);
        neg_Q.add(rt);
        
        dispatch();
        return rt;
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        cumulativeTaskCPUTime.put(this.getTask().getID(),cumulativeTaskCPUTime.get(this.getTask().getID())-this.getTimeOnCPU());

        if(this.getStatus() == ThreadRunning){
            this.getTask().setCurrentThread(null);
            this.setStatus(ThreadKill);
        }
        else if(this.getStatus()==ThreadReady){
            this.setStatus(ThreadKill);
            removeFromReadyQ(this);
        }
        else if (this.getStatus()>=ThreadWaiting){
            this.setStatus(ThreadKill);
            for(int i=0;i<Device.getTableSize();i++){
                Device.get(i).cancelPendingIO(this);
            }
        }

        ResourceCB.giveupResources(this);
        TaskCB task = this.getTask();
        task.removeThread(this);
        if(task.getThreadCount()==0){
            task.kill();
            cumulativeTaskCPUTime.remove(task.getID());
        }
        dispatch();
    }


    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public  void do_suspend(Event event)
    {
        
        if(this.getStatus()==ThreadRunning){
        	this.setStatus(ThreadWaiting);
            this.getTask().setCurrentThread(null);
            event.addThread(this);
            dispatch();
        }else if(this.getStatus()>=ThreadWaiting){
            this.setStatus(this.getStatus()+1);
            event.addThread(this);
            dispatch();
        }else{
           // System.out.println("Wrong: suspend thread in status other than running or waiting");
        }
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public  void  do_resume()
    {        
        if(this.getStatus() == ThreadWaiting){
            this.setStatus(ThreadReady);
            EXT_ThreadCB temp = (EXT_ThreadCB) this;


            // if(temp.update_priority()){
            //     pos_Q.add(temp);
            // }
            // else{
            //     neg_Q.add(temp);
            // }



            neg_Q.add(temp);


            dispatch();
        }
        else if(this.getStatus() > ThreadWaiting){
            this.setStatus(this.getStatus()-1);
            dispatch();
        }    
        else{
           // System.out.println("Wrong: try to resume a thread in the status other than ThreadWaiting");
        }
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

	    //@kg: context switch is split into two part , preempting is the first part and do_dispatch is the second part
	
        @OSPProject Threads
    */
    public static int do_dispatch()
    {

        preemptingCRT();    // preempting first
    	ThreadCB theone =  null;


        //printRQS();


        if(first_2_go.size() >0){
            //pick from first_2_go Q
            theone = first_2_go.remove(0);
        }else{
            if(pos_Q.size()>0){
                Collections.sort(pos_Q);
                theone = pos_Q.remove(0);           //@kg????!!!: in lack of other stratergy
            }else if(neg_Q.size()>0){
                Collections.sort(neg_Q);
                theone = neg_Q.remove(0);
            }else{
                theone = null;
            }
        }
                                 // @kg???!!!: what if the case that from null to t or from t to null
        if(theone == null){
          //  System.out.println("no thread to run");
            HTimer.set(100);
            return FAILURE;                            
        }else{
            theone.setStatus(ThreadRunning);    //step one
            MMU.setPTBR(theone.getTask().getPageTable());   //step two
            MMU.getPTBR().getTask().setCurrentThread(theone);   // step three
            HTimer.set(100);                                            //reset the time to 100
            return SUCCESS;
        }
    }







    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        System.out.println("????????????????????????-- -- - ---- Warning");
    }


    public static double getCumulativeTaskCPUTime(int id){
        if(cumulativeTaskCPUTime.containsKey(id))
            return cumulativeTaskCPUTime.get(id);
        else
            return -1.0;
    }


    private static void removeFromReadyQ(ThreadCB thread){
            first_2_go.remove(thread);
            neg_Q.remove(thread);
            pos_Q.remove(thread);
    }

    //@kg: preempttingCRT2()
    private static void preemptingCRT(){
        long addedWaiting_time = HTimer.get();
        boolean f2g =addedWaiting_time > 0?true:false;
        if(MMU.getPTBR()==null){
            return;
        }
        TaskCB task = MMU.getPTBR().getTask();
        if(task.getThreadCount()==0 && task.getCurrentThread()==null){  //no current running thread
            //System.out.println("no running thread right now");
        }
        else if(task.getThreadCount()!=0 && task.getCurrentThread()!=null){
            EXT_ThreadCB thread = (EXT_ThreadCB) task.getCurrentThread();
            thread.setStatus(ThreadReady);
            //adding waiting time to all RD threads
            cumulativeTaskCPUTime.put(task.getID(),cumulativeTaskCPUTime.get(task.getID()) + addedWaiting_time) ; // update the Task_threads_cpu_time
            if(f2g ){    // no need to update the priority , only need to adding the waiting time for all threads
                //@kg !!!! : modified here     || first_2_go.size()!=0
                for(int i=0;i<first_2_go.size();i++){
                    first_2_go.get(i).total_waiting_time+=addedWaiting_time;
                }
                for(int i=0;i<pos_Q.size();i++){
                    pos_Q.get(i).total_waiting_time+=addedWaiting_time;
                }
                for(int i=0;i<neg_Q.size();i++){
                    neg_Q.get(i).total_waiting_time+=addedWaiting_time;
                }
            }else{
                for(int i=0;i<first_2_go.size();i++){
                    first_2_go.get(i).total_waiting_time+=addedWaiting_time;
                    first_2_go.get(i).update_priority();
                }
                for(int i=0;i<pos_Q.size();i++){
                    pos_Q.get(i).total_waiting_time+=addedWaiting_time;
                    pos_Q.get(i).update_priority();
                }
                for(int i=0;i<neg_Q.size();i++){
                    EXT_ThreadCB temp = neg_Q.get(i);
                    temp.total_waiting_time+=addedWaiting_time;
                    if(temp.update_priority()){
                        neg_Q.remove(temp);
                        pos_Q.add(temp);
                    }
                }
            }
            if(f2g){            //height priority : add to first_2_go Q
                first_2_go.add(thread);
            }else{              // add to RD Q
                if(thread.update_priority()){
                    pos_Q.add(thread);
                }else{
                    neg_Q.add(thread);
                }
            }
        }
        else{
            // the case , when CRT change statues to waiting or kill
            //System.out.println("no current running thread for this task , there exist suspend thread");
        }

        MMU.setPTBR(null);              //step 2 of preempt
        task.setCurrentThread(null);       //step 3 of preempt
    }


    @Override
    public int compareTo(Object o) {
        // TODO Auto-generated method stub
        EXT_ThreadCB one = (EXT_ThreadCB)o;
        if(this.getPriority()>one.getPriority())
            return 1;
        else if (this.getPriority()==one.getPriority())
            return 0;
        else
            return -1;
    }



    // debuging
    private static void printRQS(){
    System.out.println(">>>>>>>>>>>>>>>>>>>------------");
    System.out.println("first_2_go size : "+first_2_go.size());
    System.out.println("contains: ");
    for(int i=0;i<first_2_go.size();i++)
        System.out.print("|"+first_2_go.get(i).getID());
    System.out.println("");
    System.out.println("pos_Q size : "+pos_Q.size());
    System.out.println("contains: ");
    for(int i=0;i<pos_Q.size();i++)
        System.out.print("|"+pos_Q.get(i).getID());
    System.out.println("");
    System.out.println("neg_Q size : "+neg_Q.size());
    System.out.println("contains: ");
    for(int i=0;i<neg_Q.size();i++)
        System.out.print("|"+neg_Q.get(i).getID());
    System.out.println("");
    System.out.println(">>>>>>>>>>>>>>>>>>>------------");
    }

    
}

/*
      Feel free to add local classes to improve the readability of your code
*/
