package april.dynamixel;

import java.io.*;

import april.jserial.*;
import april.util.*;

public class DynamixelTest
{
    public static void main(String args[]) throws IOException
    {
        GetOpt gopt = new GetOpt();
        gopt.addString('d', "device", "/dev/ttyUSB0", "USBDynamixel device path, or 'sim'");
        gopt.addInt('b', "baud", 1000000, "Baud rate");
        gopt.addSeparator();
        gopt.addInt('i', "id", -1, "Execute a command for a specific servo");
        gopt.addBoolean('\0', "home", false, "Command the servo to zero degrees");
        gopt.addBoolean('\0', "home-all", false, "Command all servos to zero degrees");
        gopt.addBoolean('\0', "idle", false, "Command the servo to zero torque");
        gopt.addInt('\0', "set-id", 0, "Set the servo's ID");
        gopt.addInt('\0', "set-baud", 0, "Set the servo's baud rate");
        gopt.addDouble('\0', "set-degrees", 0, "Set the servo's position");
        gopt.addSeparator();
        gopt.addDouble('\0', "speed", .5, "Speed setting for interactive mode");
        gopt.addDouble('\0', "torque", .5, "Torque setting for interactive mode");
        gopt.addInt('\0', "max-id", 253, "Maximum ID to poll over");

        gopt.addSeparator();
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

        if (gopt.getBoolean("home-all")) {
            for (int id = 0; id < gopt.getInt("max-id"); id++) {
                System.out.printf("homing servo %d\n", id);
                AbstractServo servo = bus.getServo(id);
                if (servo == null)
                    continue;
                servo.setGoal(0, gopt.getDouble("speed"), gopt.getDouble("torque"));
            }
        }

        if (gopt.getInt("id") >= 0) {

            int id = gopt.getInt("id");

            AbstractServo servo = bus.getServo(id);
            if (servo == null) {
                System.out.printf("Couldn't find a servo at id %d\n", id);
                return;
            }

            if (gopt.getBoolean("home")) {
                System.out.printf("Commanding servo %d home\n", id);
                servo.setGoal(0, gopt.getDouble("speed"), gopt.getDouble("torque"));
            }

            if (gopt.getBoolean("idle")) {
                System.out.printf("Commanding servo %d idle\n", id);
                servo.idle();
            }

            if (gopt.wasSpecified("set-id")) {
                System.out.printf("Changing servo id %d to %d\n", id, gopt.getInt("set-id"));
                servo.setID(gopt.getInt("set-id"));
            }

            if (gopt.wasSpecified("set-baud")) {
                System.out.printf("Changing servo id %d baud-rate to %d\n", id, gopt.getInt("set-baud"));
                servo.setBaud(gopt.getInt("set-baud"));
            }

            if (gopt.wasSpecified("set-degrees")) {
                System.out.printf("Setting servo id %d to %f degrees\n", id, gopt.getDouble("set-degrees"));
                servo.setGoal(Math.toRadians(gopt.getDouble("set-degrees")), .5, .5);
            }
        }

        // Begin interactive mode.
        // You can type '1' through '9' (followed by enter) to set position.
        if (true) {

            double speed = gopt.getDouble("speed");
            double torque = gopt.getDouble("torque");

            int id0, id1;
            if (gopt.wasSpecified("id")) {
                id0 = gopt.getInt("id");
                id1 = id0;
            } else {
                id0 = 0;
                id1 = gopt.getInt("max-id");
            }


            while (true) {

                if (id0 != id1)
                    System.out.println("");

                for (int id = id0; id <= id1; id++) {

                    System.out.printf("%d...\r", id);
                    System.out.flush();

                    AbstractServo servo = bus.getServo(id);
                    if (servo == null)
                        continue;

                    System.out.printf("id %3d : %s v %-3d [ %s ]\n",
                                      id,
                                      servo.getClass().getSimpleName(),
                                      servo.getFirmwareVersion(),
                                      servo.getStatus());

                    if (System.in.available() > 0) {
                        int c = System.in.read();
                        if (c >= '1' && c <= '9') {
                            double v = 1.0 * (c - '1') / ('9' - '1');
                            servo.setGoal(Math.toRadians(359.99999) * v - Math.PI, speed, torque);
                        }

                        if (c >= 'a' && c <= 'z') {
                            id = c - 'a' + 1;
                            id0 = id;
                            id1 = id;
                        }
                    }
                }
            }
        }
        // end of interactive mode

    }
}
