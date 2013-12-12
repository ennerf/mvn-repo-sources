package april.vis;

import java.util.concurrent.*;

public class GLManager
{
    ArrayBlockingQueue<Task> tasks = new ArrayBlockingQueue<Task>(10);

    static GLManager singleton = new GLManager();

    private GLManager()
    {
        new RunThread().start();
    }

    public static GLManager getSingleton()
    {
        return singleton;
    }

    public interface Task
    {
        public void run();
    }

    /** Schedule a task to be run on the GL thread. If the task is
     * already scheduled to run, the task is not scheduled again. **/
    public boolean add(Task t)
    {
        synchronized(tasks) {
            if (tasks.contains(t))
                return true;
            return tasks.offer(t);
        }
    }

    class RunThread extends Thread
    {
        public RunThread()
        {
            setDaemon(true);
        }

        public void run()
        {
            GL.initialize();

            while (true) {
                Task t;

                try {
                    t = tasks.take();
                } catch (InterruptedException ex) {
                    System.out.println("GLManager interrupted "+ex);
                    break;
                }

                try {
                    t.run();
                } catch (Exception ex) {
                    System.out.println("GLManager task "+t+" had exception "+ex);
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }
}
