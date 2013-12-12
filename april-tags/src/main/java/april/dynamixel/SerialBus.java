package april.dynamixel;

import java.io.*;

import april.jserial.*;
import april.util.*;

public class SerialBus extends AbstractBus
{
    int TIMEOUT_MS = 50;

    JSerial js;
    boolean verbose = false;

    public SerialBus(JSerial js)
    {
        this.js = js;
    }

    public void setBaud(int baud) throws IOException
    {
        js.setBaud(baud);
    }

    public int getBaud()
    {
        return js.getBaud();
    }

    public synchronized byte[] sendCommand(int id, int instruction, byte parameters[], boolean retry)
    {
        do {
            byte resp[] = sendCommandRaw(id, instruction, parameters);

            if (resp == null || resp.length < 1) {
                if (verbose)
                    System.out.printf("SerialBus id=%d error: short response.\n", id);
                continue;
            }

            if (resp[0] != 0) {
                int code = (resp[0]) & 0xff;
//              System.out.printf("SerialBus id=%d error 0x%02x: %s\n", id, code, AbstractServo.Status.getErrorString(code, "OK"));

                int errormask = AbstractServo.ERROR_ANGLE_LIMIT |
                    AbstractServo.ERROR_VOLTAGE |
                    AbstractServo.ERROR_OVERLOAD;

                if ((code & (~errormask)) != 0)
                    continue;
            }

            return resp;

        } while (retry && retryEnable);

        return null;
    }

    /**
     * Send an instruction with the specified parameters. The error
     * code, body and checksum of the response are returned (the
     * initial 4 bytes of header are removed)
     **/
    protected byte[] sendCommandRaw(int id, int instruction, byte parameters[])
    {
        synchronized (js)
        {
            int parameterlen = (parameters == null) ? 0 : parameters.length;
            byte cmd[] = new byte[6 + parameterlen];
            cmd[0] = (byte) 255; // magic
            cmd[1] = (byte) 255; // magic
            cmd[2] = (byte) id; // servo id
            cmd[3] = (byte) (parameterlen + 2); // length
            cmd[4] = (byte) instruction;

            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++)
                    cmd[5 + i] = parameters[i];
            }

            if (true) {
                // compute checksum
                int checksum = 0;
                for (int i = 2; i < cmd.length - 1; i++)
                    checksum += (cmd[i] & 0xff);
                cmd[5 + parameterlen] = (byte) (checksum ^ 0xff);
            }

            int res = js.write(cmd, 0, cmd.length);
            if (verbose) {
                System.out.printf("WRITE: res = %d len = %d : ", res, cmd.length);
                dump(cmd);
            }

            /////////////////////////////////
            // Read response. The header is really 5 bytes, but we put
            // the error code in the body, so that the caller knows
            // what went wrong if something bad happens.  synchronize
            // on the first two 0xffff characters
            byte header[] = new byte[4];
            int header_have = 0;

            while (header_have < 4) {
                res = js.readFullyTimeout(header, header_have, 4 - header_have, TIMEOUT_MS);

                if (verbose) {
                    System.out.printf("READ:  res = %d : ", res);
                    dump(header);
                }

                if (res < 1)
                    return null;

                assert (res <= (4 - header_have));
                assert (res + header_have == 4);

                // if first two bytes are the sync bytes, we're done.
                if ((header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xff)
                    break;

                // shift buffer, read one more character
                header_have = 3;
                for (int i = 0; i < 3; i++)
                    header[i] = header[i + 1];
            }

            if ((header[2] & 0xff) != id) {
                System.out.printf("SerialBus: Received response for wrong servo %d\n", header[2] & 0xff);
                return null;
            }

            int thisid = header[2] & 0xff;
            int length = header[3] & 0xff;

            if (length < 2)
                return null;

            byte body[] = new byte[length];
            res = js.readFullyTimeout(body, 0, body.length, TIMEOUT_MS);

            if (verbose) {
                System.out.printf("READ:  res = %d : ", res);
                dump(body);
            }

            if (true) {
                // compute checksum
                int checksum = 0;
                for (int i = 2; i < header.length; i++)
                    checksum += (header[i] & 0xff);
                for (int i = 0; i < body.length - 1; i++)
                    checksum += (body[i] & 0xff);
                checksum = (checksum & 0xff) ^ 0xff;
                if ((body[body.length - 1] & 0xff) != checksum) {
                    System.out.printf("SerialBus: Bad checksum %02x %02x\n", body[body.length - 1] & 0xff, checksum);
                    return null;
                }
            }

            return body;
        }
    }

    static void dump(byte buf[])
    {
        for (int i = 0; i < buf.length; i++)
            System.out.printf("%02x ", buf[i] & 0xff);

        System.out.printf("\n");
    }
}
