package april.dynamixel;

import java.io.*;
import java.util.*;

import april.jserial.*;
import april.util.*;

public class SimBus extends AbstractBus
{
    Device devices[] = new Device[256];
    Timer timer = new Timer();
    int messageDelay_ms;

    interface Device
    {
        // returns the read value
        public int read(int addr);

        // returns error code
        public int write(int addr, int value);

        public int getErrorCode();
    }

    static class ServoDevice extends TimerTask implements Device
    {
        int id;
        int model;
        double radiansRange; // e.g. toRadians(300) for AX12
        int    radiansQuant; // e.g. 1024 for AX12

        double radiansGoal = 0; // e.g. 0 to 2PI.
        double radiansState = 0;
        double speedGoal = 1;
        double speedState = 0;
        double torqueGoal = 1;
        double torqueState = 0;

        double speedScale = 1;

        byte registers[] = new byte[256];

        static int UPDATE_PERIOD = 1000 / 50;

        public ServoDevice()
        {
        }

        public int getErrorCode()
        {
            return 0;
        }

        public int read(int addr)
        {
            int tmp;

            switch (addr) {
                case 0:
                    return model & 0xff;
                case 1:
                    return model >> 8;
                case 2:
                    return 10; // model
                case 3:
                    return id;
                case 4:
                    return 1; // baud
                case 6:
                    return 1; // rotation mode (0 = continuous, else wheel)
                case 36: // position
                case 37:
                    tmp = (int) (radiansQuant * radiansState / radiansRange);
                    if (addr == 36)
                        return tmp & 0xff;
                    return tmp >> 8;
                case 38: // speed
                case 39:
                    if (speedState > 0)
                        tmp = (int) (speedState / 10 * 1023);
                    else
                        tmp = (int) (-speedState / 10 * 1023 + 1024);
                    if (addr == 38)
                        return tmp & 0xff;
                    return tmp >> 8;
                case 40: // load (we'll just use -speed)
                case 41:
                    double s = -speedState;
                    if (s > 0)
                        tmp = (int) (s / 10 * 1023);
                    else
                        tmp = (int) (-s / 10 * 1023 + 1024);
                    if (addr == 40)
                        return tmp & 0xff;
                    return tmp >> 8;
                case 0x2a: // voltage * 10
                    return 96;
                case 0x2b: // degrees celsius
                    return 25;

            }
            return 0;
        }

        public int write(int addr, int value)
        {
            switch (addr) {
                case 30: // goal position
                case 31:
                    registers[addr] = (byte) value;
                    if (addr == 31) {
                        int tmp = (registers[30] & 0xff) + ((registers[31]&0xff)<<8);
                        radiansGoal = radiansRange * tmp / (1.0*radiansQuant);
                    }
                    break;

                case 32: // goal speed
                case 33:
                    break;

                case 34: // torque limit
                case 35:
                    break;
            }

            return 0;
        }

        public synchronized void run()
        {
            double dirsign = (radiansGoal >= radiansState) ? 1 : -1;
            speedState = dirsign * speedScale * speedGoal;
            radiansState += speedState / (1000 / UPDATE_PERIOD);

            if (dirsign > 0 && radiansState > radiansGoal)
                radiansState = radiansGoal;

            if (dirsign < 0 && radiansState < radiansGoal)
                radiansState = radiansGoal;

            if (radiansState == radiansGoal)
                speedState = 0;
        }
    }

    public SimBus()
    {
        messageDelay_ms = 10;
    }

    public SimBus(int messageDelay_ms)
    {
        this.messageDelay_ms = messageDelay_ms;
    }

    public void addAX12(int id)
    {
        ServoDevice dev = new ServoDevice();
        dev.id = id;
        dev.model = 0x000c;
        dev.radiansRange = Math.toRadians(300);
        dev.radiansGoal = dev.radiansRange / 2;
        dev.radiansState = dev.radiansGoal;
        dev.radiansQuant = 1024;
        dev.speedScale = 3;

        timer.schedule(dev, 0, dev.UPDATE_PERIOD);

        devices[id] = dev;
    }

    public void addMX28(int id)
    {
        ServoDevice dev = new ServoDevice();
        dev.id = id;
        dev.model = 0x001d;
        dev.radiansRange = Math.toRadians(360);
        dev.radiansGoal = dev.radiansRange / 2;
        dev.radiansState = dev.radiansGoal;
        dev.radiansQuant = 4096;
        dev.speedScale = 3;

        timer.schedule(dev, 0, dev.UPDATE_PERIOD);

        devices[id] = dev;
    }

    public byte[] sendCommand(int id, int instruction, byte parameters[], boolean retry)
    {
        Device dev = devices[id];

        if (dev == null)
            return null;

        // delay as if a real command was sent
        TimeUtil.sleep(messageDelay_ms);

        switch (instruction) {
            case AbstractBus.INST_PING:
                break;

            case AbstractBus.INST_READ_DATA:
            {
                int addr = parameters[0] & 0xff;
                int length = parameters[1] & 0xff;

                byte resp[] = new byte[length+2]; // [error, <data>, checksum]
                resp[length+1] = 0;

                synchronized(dev) {
                    for (int i = 0; i < length; i++)
                        resp[i+1] = (byte) dev.read(addr+i);
                    resp[0] = (byte) dev.getErrorCode();
                }

                return resp;
            }

            case AbstractBus.INST_WRITE_DATA:
            {
                int addr = parameters[0] & 0xff;
                int length = parameters.length - 1;

                byte resp[] = new byte[2];  // [error, checksum]
                resp[1] = 0;

                int error = 0;

                synchronized(dev) {
                    for (int i = 0; i < length; i++)
                        error |= dev.write(addr+i, parameters[i+1]);

                    resp[0] = (byte) (error | dev.getErrorCode());
                }

                return resp;
            }

            case AbstractBus.INST_REG_WRITE:
            case AbstractBus.INST_ACTION:
            case AbstractBus.INST_RESET_DATA:
            case AbstractBus.INST_SYNC_WRITE:
                // unimplemented command.
                assert(false);
                break;

            default:
                // unknown command.
                assert(false);
                break;
        }

        return null;
    }
}

