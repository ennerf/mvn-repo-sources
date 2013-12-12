package april.vis;

import java.io.*;

/** You must implement a constructor like this:
 *
 *     VisSerializable(ObjectReader ins);
 *
 * Note that during deserialization, the constructor will be called
 * with null, then readObject will immediately be called with a valid
 * ObjectReader. This protocol is used to allow a serializable object
 * to have public constructor that won't be confused as a user API.
 **/
public interface VisSerializable
{
    public void writeObject(ObjectWriter outs) throws IOException;

    public void readObject(ObjectReader ins) throws IOException;
}
