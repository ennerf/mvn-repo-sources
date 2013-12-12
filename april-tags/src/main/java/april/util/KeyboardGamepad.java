package april.util;


import javax.swing.*;
import java.util.*;
import java.awt.event.*;

import lcm.lcm.*;

import april.util.*;
import april.lcmtypes.*;

public class KeyboardGamepad implements KeyListener, Runnable
{
    public boolean running = true;

    // keeps track of when a key was first
    HashMap<Integer,Long> startUtimes = new HashMap<Integer,Long>();
    HashMap<Integer,Long> endUtimes = new HashMap<Integer,Long>();

    static int sleepMS = 25;


    public void run()
    {
        while(true) {
            TimeUtil.sleep(sleepMS);

            if (!running)
                continue;

            // Else, figure out how to spoof a gamepad message
            gamepad_t gp = new gamepad_t();
            gp.naxes = 6; // for compatability -- we only use the last two
            gp.axes = new double[gp.naxes];
            gp.present = true;

            synchronized (this) {
                gp.utime = TimeUtil.utime();

                long utimeThresh = (sleepMS*8)*100;

                // check L/R
                if (isDown(gp.utime,KeyEvent.VK_RIGHT)) {
                    gp.axes[4] = 1.0;
                } else if (isDown(gp.utime,KeyEvent.VK_LEFT)) {
                    gp.axes[4] = -1.0;
                } else {
                    gp.axes[4] = 0.0;
                }

                if (isDown(gp.utime,KeyEvent.VK_DOWN)) {
                    gp.axes[5] = 1.0;
                } else if (isDown(gp.utime,KeyEvent.VK_UP)) {
                    gp.axes[5] = -1.0;
                } else {
                    gp.axes[5] = 0.0;
                }


                if (isDown(gp.utime,KeyEvent.VK_SPACE))
                    gp.buttons = gp.buttons | 1;
                if (isDown(gp.utime,KeyEvent.VK_ENTER))
                    gp.buttons = gp.buttons | 2;
                if (isDown(gp.utime,KeyEvent.VK_SHIFT))
                    gp.buttons = gp.buttons | 4;
                if (isDown(gp.utime,KeyEvent.VK_ALT))
                    gp.buttons = gp.buttons | 8;
                if (isDown(gp.utime,KeyEvent.VK_CONTROL))
                    gp.buttons = gp.buttons | 16;
            }
            LCM.getSingleton().publish("GAMEPAD",gp);
        }
    }

    public synchronized boolean isDown(long utime, int keycode)
    {
        // if the key hasn't been released yet
        if (startUtimes.get(keycode) == null)
            return false;
        if (endUtimes.get(keycode) == null)
            return true;

        if (startUtimes.get(keycode) > endUtimes.get(keycode))
            return true;

        if ((utime - endUtimes.get(keycode)) < 40000)
            return true;
        return false;
    }

    public synchronized long getLastKeyUtime(int keycode)
    {
        Long val = startUtimes.get(keycode);
        if (val == null)
            return 0;
        return val;
    }

    public synchronized void keyPressed(KeyEvent e)
    {
        startUtimes.put(e.getKeyCode(), TimeUtil.utime());
    }

    public synchronized void keyReleased(KeyEvent e)
    {
        endUtimes.put(e.getKeyCode(), TimeUtil.utime());
    }

    public synchronized void keyTyped(KeyEvent e)
    {
    }


    public static void main(String args[])
    {
        KeyboardGamepad  kgp = new KeyboardGamepad();


        JFrame jf = new JFrame("Keyboard Gamepad");
        jf.addKeyListener(kgp);

        jf.setSize(150,80);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        kgp.run();

    }
}
