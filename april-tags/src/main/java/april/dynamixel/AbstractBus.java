package april.dynamixel;

public abstract class AbstractBus
{
    public static final int INST_PING         = 0x01;
    public static final int INST_READ_DATA    = 0x02;
    public static final int INST_WRITE_DATA   = 0x03;
    public static final int INST_REG_WRITE    = 0x04;
    public static final int INST_ACTION       = 0x05;
    public static final int INST_RESET_DATA   = 0x06;
    public static final int INST_SYNC_WRITE   = 0x83;

    boolean retryEnable = true;

    /** Send an instruction with the specified parameters. The error
     * code, body and checksum of the response are returned (the
     * initial 4 bytes of header are removed.)
     *
     * @param retry If true, commands that result in an error (except
     * for certain non-fatal errors) will be retried.
     **/
    public abstract byte[] sendCommand(int id, int instruction, byte parameters[], boolean retry);

    public void setRetryEnable(boolean retryEnable)
    {
        this.retryEnable = retryEnable;
    }

    /** Returns the model identifier for the servo or -1 if no servo found. **/
    public int getServoModel(int id)
    {
        byte resp[] = sendCommand(id, AbstractBus.INST_READ_DATA, new byte[] { 0x00, 3 }, false);
        if (resp == null)
            return -1;

        return (resp[1] & 0xff) + ((resp[2] & 0xff) << 8);
    }

    public AbstractServo getServo(int id)
    {
        int model = getServoModel(id);
        if (model < 0)
            return null;

        switch(model) {
            case 0x000c: // definitely for AX12+. Do other AX12 variants have same id?
                return new AX12Servo(this, id);

            case 0x001d:
                return new MX28Servo(this, id);

            case 0x0136:
                return new MX64Servo(this, id);

            case 0x0140:
                return new MX106Servo(this, id);
        }

        System.out.printf("AbstractBus: Unknown servo type %04x at id %d\n", model, id);
        return null;
    }
}
