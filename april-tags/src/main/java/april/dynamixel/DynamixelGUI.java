package april.dynamixel;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jserial.*;
import april.util.*;

public class DynamixelGUI
{
    AbstractBus bus;
    JFrame jf;

    JPanel servoPane = new JPanel();

    ServoPanel servoPanels[] = new ServoPanel[255];

    JLabel pollLabel = new JLabel();

    int MIN_ID = 0;
    int MAX_ID = 16;

    public DynamixelGUI(AbstractBus bus)
    {
        this.bus = bus;

        bus.setRetryEnable(false);

        jf = new JFrame("DynamixelGUI");
        jf.setLayout(new BorderLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        if (bus instanceof SerialBus) {
            jf.add(new SerialBusPanel(), BorderLayout.NORTH);
        }

        servoPane.setLayout(new VFlowLayout());

        for (int i = 0; i < servoPanels.length; i++) {
            servoPanels[i] = new ServoPanel(i);
            servoPane.add(servoPanels[i]);
        }

        jf.add(pollLabel, BorderLayout.SOUTH);

        jf.add(new JScrollPane(servoPane), BorderLayout.CENTER);
//        jf.add(servoPane, BorderLayout.CENTER);
        jf.setSize(800,800);
        jf.setVisible(true);

        new EnumerateThread().start();
    }

    class SerialBusPanel extends JPanel
    {
        String bauds[] = new String[] { "57600", "500000", "1000000" };
        JComboBox jcb = new JComboBox(bauds);

        public SerialBusPanel()
        {
            setLayout(new BorderLayout());
            add(new JLabel("SerialBus Options"), BorderLayout.NORTH);
            add(new JLabel("Baud rate"), BorderLayout.WEST);
            add(jcb, BorderLayout.CENTER);

            int baud = ((SerialBus) bus).getBaud();

            for (int i = 0; i < bauds.length; i++) {
                if (Integer.parseInt(bauds[i])==baud)
                    jcb.setSelectedIndex(i);
            }

            jcb.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int baud = Integer.parseInt((String) jcb.getSelectedItem());

                    synchronized(DynamixelGUI.this) {
                        try {
                            ((SerialBus) bus).setBaud(baud);
                        } catch (IOException ex) {
                            System.out.println("ex: "+ex);
                        }

                        for (int i = 0; i < servoPanels.length; i++) {
                            servoPanels[i].servo = null;
                        }
                    }
                }
            });
        }
    }

    class ServoPanel extends JPanel
    {
        int           id;
        AbstractServo servo;

        int WIDTH = 400;
        int HEIGHT = 150;

        JLabel idLabel = new JLabel();

        FeedbackSlider positionSlider = new FeedbackSlider(-1, 1, 0, 0, true);
        FeedbackSlider speedSlider = new FeedbackSlider(0, 1, .1, 0, true);
        FeedbackSlider torqueSlider = new FeedbackSlider(0, 1, .3, 0, true);

        JLabel statusLabel = new JLabel();

        JPopupMenu popupMenu = new JPopupMenu();

        ServoPanel(int id)
        {
            this.id = id;
            setSize(0, 0);

            setLayout(new BorderLayout());
            add(idLabel, BorderLayout.NORTH);

            JPanel jp = new JPanel();
            jp.setLayout(new GridLayout(4,1));
            jp.add(positionSlider);
            jp.add(speedSlider);
            jp.add(torqueSlider);
            jp.add(statusLabel);

            positionSlider.setFormatStrings("Goal Position %.3f", "Actual Position %.3f");
            speedSlider.setFormatStrings("Max Speed %.3f", "Current Speed %.3f");
            torqueSlider.setFormatStrings("Max Torque %.3f", "Current load %.3f");

            add(jp, BorderLayout.CENTER);

            setBorder(BorderFactory.createLineBorder(Color.black));

            positionSlider.addListener(new FeedbackSlider.Listener() {
                public void goalValueChanged(FeedbackSlider ss, double goalvalue) {
                    sendCommand();
                }
            });

            speedSlider.addListener(new FeedbackSlider.Listener() {
                public void goalValueChanged(FeedbackSlider ss, double goalvalue) {
                    sendCommand();
                }
            });

            torqueSlider.addListener(new FeedbackSlider.Listener() {
                public void goalValueChanged(FeedbackSlider ss, double goalvalue) {
                    sendCommand();
                }
            });

            if (true) {
                JMenu idMenu = new JMenu("Set ID");
                for (int i = 0; i < MAX_ID; i++) {
                    JMenuItem item = new JMenuItem(""+i);
                    idMenu.add(item);
                    item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            int id = Integer.parseInt(e.getActionCommand());
                            synchronized(DynamixelGUI.this) {
                                servo.setID(id);
                                servo = null;
                            }
                        }
                    });
                }

                JMenu baudMenu = new JMenu("Set Baud");
                int bauds[] = new int[] { 57600, 500000, 1000000 };
                for (int i = 0; i < bauds.length; i++) {
                    JMenuItem item = new JMenuItem(""+bauds[i]);
                    baudMenu.add(item);
                    item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            int baud = Integer.parseInt(e.getActionCommand());
                            synchronized(DynamixelGUI.this) {
                                servo.setBaud(baud);
                                servo = null;
                            }
                        }
                    });
                }

                JMenu modeMenu = new JMenu("Set Mode");
                final String MODE_JOINT = "joint (with default angle limits)";
                final String MODE_WHEEL = "wheel (continuous rotation)";
                String modes[] = new String[] { MODE_JOINT, MODE_WHEEL };
                for (int i = 0; i < modes.length; i++) {
                    JMenuItem item = new JMenuItem(""+modes[i]);
                    modeMenu.add(item);
                    item.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                boolean mode = !((String)e.getActionCommand()).equals(MODE_JOINT);
                                synchronized(DynamixelGUI.this) {
                                    if (servo != null)
                                        servo.setContinuousMode(mode);
                                }
                                rotationModeChanged(mode);
                                sendCommand();
                            }
                        });
                }
                popupMenu.add(idMenu);
                popupMenu.add(baudMenu);
                popupMenu.add(modeMenu);
            }

            idLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == 3)
                        popupMenu.show(ServoPanel.this, e.getX(), e.getY());
                }
            });
        }

        void rotationModeChanged(boolean mode)
        {
            positionSlider.showgoal = !mode;
            positionSlider.repaint();

            if (!mode)
                speedSlider.setGoalValue(Math.abs(speedSlider.getGoalValue()));
            speedSlider.setMinimum(mode ? -1 : 0);
            speedSlider.repaint();
        }

        void sendCommand()
        {
            synchronized(DynamixelGUI.this) {
                if (servo != null)
                    servo.setGoal(positionSlider.getGoalValue(), speedSlider.getGoalValue(), torqueSlider.getGoalValue());
            }
        }

        public Dimension getPreferredSize()
        {
            synchronized(DynamixelGUI.this) {
                if (servo != null)
                    return new Dimension(1, HEIGHT);
            }

            return new Dimension(0, 0);
        }

        void updateState()
        {
            synchronized(DynamixelGUI.this) {
                if (servo == null)
                    return;

                AbstractServo.Status status = servo.getStatus();
                if (status == null)
                    return;

                positionSlider.setActualValue(status.positionRadians);
                double speed = status.speed;
                speedSlider.setActualValue(servo.getRotationMode() ? speed : Math.abs(speed));
                torqueSlider.setActualValue(Math.abs(status.load));
                statusLabel.setText(status.toString());
            }
        }

        public void enumerate()
        {
            boolean hadServo = (servo != null);

            servo = bus.getServo(id);

            if (servo != null) {

                if (!hadServo) {
                    idLabel.setText(String.format("ID %d : %s", id, servo.getClass().getSimpleName()));

                    if (getHeight()==0)
                        setSize(WIDTH, HEIGHT);

                    positionSlider.setMinimum(servo.getMinimumPositionRadians());
                    positionSlider.setMaximum(servo.getMaximumPositionRadians());

                    rotationModeChanged(servo.getRotationMode());

                    updateState();

                    positionSlider.setGoalValue(positionSlider.getActualValue());
                }
            } else {
                if (hadServo)
                    setSize(0, 0);
            }

            invalidate();
            repaint();
        }
    }

    class EnumerateThread extends Thread
    {
        public void run()
        {
            int nextPollID = 0;
            int nextUpdateID = 0;

            while (true) {

                pollLabel.setText(String.format("polling ID %d\n", nextPollID));
                servoPanels[nextPollID].enumerate();

                nextPollID++;
                if (nextPollID == MAX_ID)
                    nextPollID = 0;

                for (int id = 0; id < servoPanels.length; id++) {
                    servoPanels[id].updateState();
                }
            }
        }
    }

    public static void main(String args[]) throws IOException
    {
        GetOpt gopt = new GetOpt();
        gopt.addString('d', "device", "/dev/ttyUSB0", "USBDynamixel device path, or 'sim'");
        gopt.addInt('b', "baud", 1000000, "Baud rate");

        gopt.addBoolean('h', "help", false, "Show this help");

        if (!gopt.parse(args) || gopt.getBoolean("help")) {
            gopt.doHelp();
            return;
        }

        AbstractBus bus;
        String device = gopt.getString("device");

        if (device.equals("sim")) {
            SimBus sbus = new SimBus();
            sbus.addAX12(1);
            sbus.addMX28(2);
            sbus.addMX28(3);
            bus = sbus;
        } else {

            JSerial js = new JSerial(gopt.getString("device"), gopt.getInt("baud"));
            js.setCTSRTS(true);

            SerialBus sbus = new SerialBus(js);
            sbus.TIMEOUT_MS = 50;
            bus = sbus;
        }

        new DynamixelGUI(bus);
    }
}
