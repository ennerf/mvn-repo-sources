package april.camera.models;

public class InitializerUtil
{
    public static int getParameter(String parameterString, String requestedKey)
    {
        String pairs[] = parameterString.split(",");

        Integer value = null;

        for (String pair : pairs) {
            String kv[] = pair.split("=");
            if (kv.length != 2) {
                System.out.printf("Expected entry '%s' in string '%s' to split into kev-value pair. Fatal\n",
                                  pair, parameterString);
                assert(false);
            }

            String key = kv[0];
            assert(key != null);
            if (!key.equals(requestedKey))
                continue;

            value = Integer.valueOf(kv[1]);
        }

        if (value == null) {
            System.out.printf("Expected key '%s' could not be found in string '%s'. Fatal.\n",
                              requestedKey, parameterString);
            assert(false);
        }

        int v = value;
        return v;
    }
}

