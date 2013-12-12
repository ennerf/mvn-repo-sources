package april.util;

import java.io.*;
import java.util.*;

/** This class implements a basic lexer that compiles regular
 * expressions programmatically (i.e., does not use an external .yy
 * file.
 *
 * \$ represents the EOF marker. Every grammar should check for this.
 **/
public class GenericTokenizer<T>
{
    // Enabling this can output good debugging information and should
    // be done when developing your grammar. But it produces spurious
    // warnings for syntax errors...
    public boolean showDeadEnds = false;

    public static class Token<T>
    {
        public T      type;    // the type specified by the user.

        public String token;
        public int    line;    // original position in the input stream.
        public int    column;  // corresponds to *end* of token.

        public Token(T type, String token, int line, int column)
        {
            this.type = type;
            this.token = token;
            this.line = line;
            this.column = column;
        }

        public String toString()
        {
            return String.format("token \"%s\" of type %s at line %d, column %d\n", token, type, line, column-1);
        }
    }

    static final char MIN_CHAR=0;
    static final char EOF_CHAR=127;
    static final char MAX_CHAR=EOF_CHAR;

    ///////////////////////////////////////////////////////
    // NFS classes
    int nextNStateId, nextNStatePriority;

    class NState
    {
        // if a terminal node, the type of token (as given by the user).
        T      type;

        // Priority of this production; lower numbers get named
        // preferentially. E.g., "function" might match both SYMBOL
        // and KEYWORDS; if KEYWORDS is declared before SYMBOL, then
        // "function" will be labeled as a KEYWORD, even though it
        // matches both.
        int    priority;

        // If this state is reached and it's terminal (there are no
        // transitions away from this state), an output token would
        // ordinarily be generated. But if ignore is true, this token
        // is discarded instead.
        boolean ignore;

        // set of out-going edges
        ArrayList<NEdge> out = new ArrayList<NEdge>();

        int              id = nextNStateId++;

        NState()
        {
        }

        NState(T type)
        {
            this.type = type;
            this.priority = nextNStatePriority++;
        }

        public String toString()
        {
            if (ignore)
                return String.format("(state %d IGNORE)", id);
            return String.format("(state %d "+type+")", id);
        }
    }

    static String charToString(char c)
    {
        if (c == ' ')
            return "' '";
        if (c > ' ' && c < 127)
            return "'"+c+"'";
        if (c=='\r')
            return "'\\r'";
        if (c=='\n')
            return "'\\n'";
        if (c == 0)
            return "'\\0'";
        if (c==EOF_CHAR)
            return "'\\$'";
        return String.format("'\\%03d'", (int) c);
    }

    class NEdge
    {
        // range of permissible characters, c0 < c1
        char     c0, c1;

        // is this an epsilon node? if so, c0/c1 are meaningless
        boolean  epsilon;

        // the destination state
        NState   destination;

        public NEdge()
        {
            this.epsilon = true;
        }

        public NEdge(char c0, char c1)
        {
            this.c0 = c0;
            this.c1 = c1;
        }

        public String toString()
        {
            if (epsilon)
                return String.format("(edge <epsilon> to %d)", destination.id);

            return String.format("(edge [%s-%s] to %d)",
                                 charToString(c0), charToString(c1), destination.id);
        }
    }

    ///////////////////////////////////////////////////////
    // DFS classes
    int nextDStateId;

    class DState
    {
        HashSet<NState> nstatesClosure;
        int id = nextDStateId++;
        ArrayList<DEdge> out = new ArrayList<DEdge>();
        T t;
        boolean ignore;

        public String toString()
        {
            String s = String.format("(state %d "+t+" "+ignore+": nstates ", id);
            for (NState ns : nstatesClosure)
                s = s + String.format("%d ", ns.id);
            s = s + ")";
            return s;
        }
    }

    class DEdge
    {
        char c0, c1;
        DState destination;

        DEdge(char c0, char c1)
        {
            this.c0 = c0;
            this.c1 = c1;
        }

        public String toString()
        {
            return String.format("(edge [%s-%s] to %d)",
                                 charToString(c0), charToString(c1), destination.id);
        }
    }

    ///////////////////////////////////////////////////////
    // Generic Utility classes
    class StringFeeder
    {
        String s;
        int pos;
        int line = 0, col = 0; // these are the position of the LAST returned character
        char lastc = '\n';

        StringFeeder(String s)
        {
            this.s = s;
        }

        boolean hasNext()
        {
            return pos<s.length();
        }

        char next()
        {
            if (lastc == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }

            lastc = s.charAt(pos++);
            return lastc;
        }

        char peek()
        {
            return s.charAt(pos);
        }
    }

    /////////////////////////////////////////////
    // GenericTokenizer

    NState nroot;
    DState droot;
    ArrayList<DState> dstates = new ArrayList<DState>();
    boolean compiled = false;

    T errorType;

    public GenericTokenizer(T errorType)
    {
        this.errorType = errorType;
        nroot = new NState();
    }

    /** A convenience method that automatically escapes all characters
     * and treats the space character as the | operator. This is
     * useful for building regular expressions for operators without
     * lots of backslashes. **/
    public void addEscape(T t, String regex)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            switch (c)
            {
                case '(':
                case ')':
                case '^':
                case '.':
                case '$':
                case '|':
                case '\\':
                case '+':
                case '-':
                case '?':
                case '*':
                case '[':
                case ']':
                    sb.append("\\"+c);
                    break;
                case ' ':
                    sb.append("|");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        add(t, sb.toString());
    }

    /** Add a regular expression that will be tagged as type "t". If t
     * is null, then tokens matching the regular expression will be
     * discarded (useful for comments and whitespace). The order in
     * which productions are added determins their priority: a
     * production added earlier takes precedence over a later
     * production. **/
    public void add(T t, String regex, boolean ignore)
    {
        assert(!compiled);

        if (!ignore)
            assert(t != null);

        NState terminalNState = new NState(t);
        terminalNState.ignore = ignore;

        NState initialNState = new NState();
        NEdge  e = new NEdge();
        e.destination = initialNState;
        nroot.out.add(e);

        createNStates(new StringFeeder(regex), initialNState, initialNState, terminalNState);
    }

    public void add(T t, String regex)
    {
        add(t, regex, false);
    }

    public void addIgnore(String regex)
    {
        add(null, regex, true);
    }

    // Parse a regular expression, connecting states between initial and terminal.
    void createNStates(StringFeeder sf, NState initialState,
                       NState s0, NState exitState)
    {

        // Provide a dummy link to insulate this sub-expression from
        // the previous sub-expression.  Consider the regex:
        // "A+B|AC". Without this insulation, both OR branches will
        // have a common root, and the epsilon transition resulting
        // from the "+" will go back to this common root. As a
        // consequence, the regular expression would erroneously match
        // "AAAAAC".  We provide a similar extra link when handling
        // "|" below.
        if (true) {
            NState s = new NState();
            NEdge e = new NEdge();
            e.destination = s;
            s0.out.add(e);
            s0 = s;
        }

        //    previous    new                exit
        //     state     edge                state
        //
        //       s0   ----------- s1        exitState
        //
        while (sf.hasNext()) {

            char c = sf.next();

            // end of a parenthetical sub-expression.
            if (c==')') {
                NEdge e = new NEdge();
                e.destination = exitState;
                s0.out.add(e);
                return;
            }

            // end of an OR clause. We accept whatever we have, and
            // create a new parallel clause.
            if (c=='|') {
                NEdge e = new NEdge();
                e.destination = exitState;
                s0.out.add(e);

                // insulate the new OR clause from the previous clause.
                NState s = new NState();
                NEdge e2 = new NEdge();
                e2.destination = s;
                initialState.out.add(e2);
                s0 = s;
                continue;
            }

            // we now consume one atom, connecting s0 to s1.
            NState s1 = new NState();

            if (c=='(') {
                // begin a new sub-expression
                createNStates(sf, s0, s0, s1);
                // closing paren consumed by call above.

            } else if (c=='.') {

                NEdge e = new NEdge(MIN_CHAR, (char) (EOF_CHAR-1));
                e.destination = s1;
                s0.out.add(e);

            } else {

                boolean accepts[] = new boolean[MAX_CHAR+1];

                switch (c)
                {
                    case '[':
                    {
                        boolean invert = false;

                        if (sf.peek()=='^') {
                            invert = true;
                            c = sf.next();
                        }

                        while (sf.peek()!=']') {
                            int c0 = sf.next();
                            if (c0 == '\\') {
                                c0 = sf.next();
                                setAcceptFlagsForControlChar(accepts, (char) c0);
                                continue;
                            }

                            int c1 = c0;
                            if (sf.hasNext() && sf.peek()=='-') {
                                sf.next(); // consume '-'
                                c1 = sf.next();
                            }

                            for (int i = c0; i <= c1; i++)
                                accepts[i]=true;
                        }
                        sf.next(); // consume ']'

                        if (invert)
                            for (int i = 0; i < accepts.length; i++)
                                accepts[i] ^= true;
                        break;
                    }

                    case '\\':
                    {
                        c = sf.next();
                        setAcceptFlagsForControlChar(accepts, c);
                        break;
                    }
                    default:
                        accepts[c] = true;
                }

                NEdge lastEdge = null;

                for (int i = MIN_CHAR; i <= MAX_CHAR; i++) {
                    if (accepts[i]) {
                        if (lastEdge!=null && i>0 && accepts[i-1]) {
                            lastEdge.c1++;
                            continue;
                        }

                        lastEdge = new NEdge((char) i, (char) i);
                        lastEdge.destination = s1;
                        s0.out.add(lastEdge);
                    }
                }
            }

            char type = sf.hasNext() ? sf.peek() : 0;

            switch (type)
            {
                case '*':
                {
                    sf.next();
                    NEdge e = new NEdge();
                    e.destination = s1;
                    s0.out.add(e);
                    e = new NEdge();
                    e.destination = s0;
                    s1.out.add(e);
                    break;
                }
                case '+':
                {
                    sf.next();
                    NEdge e = new NEdge();
                    e.destination = s0;
                    s1.out.add(e);
                    break;
                }
                case '?':
                {
                    sf.next();
                    NEdge e = new NEdge();
                    e.destination = s1;
                    s0.out.add(e);
                    break;
                }
            }

            s0 = s1;
        }

        NEdge e = new NEdge();
        e.destination = exitState;
        s0.out.add(e);
    }

    void setAcceptFlagsForControlChar(boolean accepts[], char c)
    {
        if (c=='n')
            accepts[(int) '\n'] = true;
        else if (c=='t')
            accepts[(int) '\t'] = true;
        else if (c=='r')
            accepts[(int) '\r'] = true;
        else if (c=='s') {
            accepts[(int) ' '] = true;
            accepts[(int) '\n'] = true;
            accepts[(int) '\r'] = true;
            accepts[(int) '\t'] = true;
        } else if (c=='$') {
            accepts[EOF_CHAR] = true;
        } else {
            accepts[(int) c] = true;
        }
    }

    ////////////////////////////////////////////////////////////////
    // Create DFS from the NFS
    void compile()
    {
        assert(!compiled);    // please only compile once.
        compiled = true;

        Stack<DState> queue = new Stack<DState>();

        HashSet<NState> rootClosure = epsilonClosure(nroot);
        droot = getDState(rootClosure);

        queue.push(droot);

        compile(queue, new HashSet<DState>());

        for (DState dstate : dstates) {
            if (dstate.t == null && !dstate.ignore) {

                if (showDeadEnds) {
                    // is it really a dead-end state? Compute the set of
                    // edges that transition out. It might be exhaustive!
                    boolean accepts[] = new boolean[MAX_CHAR+1];
                    for (DEdge de : dstate.out) {
                        for (char c = de.c0; c <= de.c1; c++)
                            accepts[(int) c] = true;
                    }

                    boolean deadEnd = false;
                    for (int i = 0; i < accepts.length; i++)
                        if (!accepts[i])
                            deadEnd = true;

                    if (!deadEnd)
                        continue;

                    System.out.printf("Dead-end dstate: "+dstate+"\n");
                    String seq = findDeadStateInputSequence(dstate.id);
                    if (seq != null) {
                        System.out.printf("  A sequence that produces it: "+seq);
                        System.out.printf("[ ");


                        int i0 = 0;
                        boolean some = false;
                        while (i0 < accepts.length) {
                            if (accepts[i0]) {
                                i0++;
                                continue;
                            }

                            int i1 = i0;
                            while (i1+1 < accepts.length && !accepts[i1+1])
                                i1++;
                            if (i0 == i1) {
                                System.out.printf("%s ", escape((char) i0));
                            } else if (i1 == EOF_CHAR) {
                                if (i0 == (i1-1))
                                    System.out.printf("%s %s ", escape((char) i0), escape(EOF_CHAR));
                                else
                                    System.out.printf("%s-%s %s ", escape((char) i0), escape((char) (EOF_CHAR-1)), escape(EOF_CHAR));
                            } else {
                                System.out.printf("%s-%s ",escape((char) i0), escape((char) i1));
                            }

                            i0 = i1+1;
                        }

                        System.out.printf("]\n\n");
                    } else {
                        System.out.printf("  Sorry, couldn't find a sequence that produces this dstate. (Try a larger depth limit?)\n\n");
                    }
                }
            }
        }
    }

    static String escape(String s)
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++)
            sb.append(escape(s.charAt(i)));
        return sb.toString();
    }

    static String escape(char c)
    {
        if (c==0)
            return "\\0";
        if (c=='.')
            return "\\.";
        if (c=='\n')
            return "\\n";
        if (c=='\r')
            return "\\r";
        if (c=='\t')
            return "\\t";
        if (c=='-')
            return "\\-";
        if (c=='\\')
            return "\\\\";
        if (c==EOF_CHAR)
            return "\\$";
        if (c<' ')
            return String.format("\\%d", (int) c);
        return ""+c;
    }

    // We will visit DStates, following edges, until we have visited
    // all the DStates.
    //
    // queue: the list of (possibly) unvisited DStates. We add to
    //        these as we follow edges.
    //
    // visited: the set of visited DStates, used to eliminate
    //        duplicate traversals.
    void compile(Stack<DState> queue, HashSet<DState> visited)
    {
        while (!queue.empty()) {

            DState s = queue.pop();

            if (visited.contains(s))
                continue;

            visited.add(s);

            // This is a new DState. Process its outgoing edges.

            // Make a list of ALL the edges that leave this set of
            // NStates.
            ArrayList<NEdge> edges = new ArrayList<NEdge>();
            for (NState st : s.nstatesClosure) {
                for (NEdge e : st.out) {
                    edges.add(e);
                }
            }

            // Now, we construct the edges away from this DState. We
            // do this by considering each possible input character
            // one at a time and computing the closure of NStates that
            // that character would lead us to. This creates an edge.
            //
            // As an optimization, if multiple input contiguous
            // characters all lead to the same closure of NStates, we
            // use a single edge, instead of one edge per character.

            // The last NStateClosure that we created an edge to.
            HashSet<NState> lastDestinations = null;
            // The last edge we created (which went to lastDestinations)
            DEdge lastDEdge = null;

            // Consider all possible input characters.
            for (char i = MIN_CHAR; i <= MAX_CHAR; i++) {

                // Compute the set of NStates that this character
                // could lead us to.
                HashSet<NState> destinations = new HashSet<NState>();

                for (NEdge e : edges) {
                    if (!e.epsilon && i>=e.c0 && i<=e.c1)
                        destinations.add(e.destination);
                }

                destinations = epsilonClosure(destinations);

                // add to the last edge if we can.
                if (lastDestinations!=null && destinations.equals(lastDestinations)) {
                    assert(lastDEdge.c1 == (i-1));
                    assert(lastDEdge.destination == getDState(destinations));
                    lastDEdge.c1 = i;
                    continue;
                }

                // it's a new edge.
                if (destinations.size() > 0) {
                    lastDEdge = new DEdge(i, i);
                    lastDEdge.destination = getDState(destinations);
                    queue.push(lastDEdge.destination);
                    s.out.add(lastDEdge);
                    lastDestinations = destinations;
                } else {
                    lastDestinations = null;
                }
            }
        }

        // some clean-up:
        // Remove pointers to the data we don't need anymore.
        // This will free up some memory.
        if (false) {
            nroot = null;
            for (DState s : dstates) {
                s.nstatesClosure = null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////
    // Look up (or create) the DState corresponding to the given
    // closure.
    DState getDState(HashSet<NState> closure)
    {
        // Have we already created a DState for this closure? If so,
        // return the exact same DState pointer.
        for (DState ds : dstates) {
            if (ds.nstatesClosure.equals(closure))
                return ds;
        }

        // It's a new DState.
        DState ds = new DState();
        ds.nstatesClosure=closure;

        // Look for the terminal NState with the maximum (lowest
        // value) priority. If there is one, then this state inherits
        // the NState's type.
        int priority = Integer.MAX_VALUE;
        for (NState nstate : closure) {
            if ((nstate.type != null || nstate.ignore) && nstate.priority < priority) {
                ds.t = nstate.type;
                ds.ignore = nstate.ignore;
                priority = nstate.priority;
            }
        }

        dstates.add(ds);
        return ds;
    }

    ////////////////////////////////////////////////////////////////
    // find all the states that can be reached from s via epsilon
    // (null) transitions.
    HashSet<NState> epsilonClosure(HashSet<NState> instates)
	{
	    HashSet<NState> closure = new HashSet<NState>();
	    Stack<NState> queue = new Stack<NState>();

	    for (NState s : instates)
            queue.push(s);

	    epsilonClosure(closure, queue);
	    return closure;
	}

    HashSet<NState> epsilonClosure(NState s)
    {
        HashSet<NState> closure = new HashSet<NState>();
        Stack<NState> queue = new Stack<NState>();

        queue.push(s);
        epsilonClosure(closure, queue);
        return closure;
    }

    void epsilonClosure(HashSet<NState> closure, Stack<NState> queue)
    {
        while (!queue.empty()) {
            NState s = queue.pop();

            if (closure.contains(s))
                continue;

            closure.add(s);
            for (NEdge e : s.out) {
                if (e.epsilon)
                    queue.push(e.destination);
            }
        }
    }

    ////////////////////////////////////////////////////////////////
    // debug/testing

    // Traverse the DFS tree, dumping its contents.
    public void dumpDroot()
    {
        if (!compiled)
            compile();

        Stack<DState> queue = new Stack<DState>();

        queue.push(droot);
        dumpDroot(queue,  new HashSet<DState>());
    }

    void dumpDroot(Stack<DState> queue, HashSet<DState> visited)
    {
        while (!queue.empty()) {

            DState s = queue.pop();

            if (visited.contains(s))
                continue;

            visited.add(s);

            System.out.println(s);

            for (DEdge e : s.out) {
                System.out.println("  "+e);

                queue.push(e.destination);
            }
        }
    }

    // Traverse the NFS tree, dumping its contents.
    public void dumpNroot()
    {
        Stack<NState> queue = new Stack<NState>();

        queue.push(nroot);
        dumpNroot(queue, new HashSet<NState>());
    }

    void dumpNroot(Stack<NState> queue, HashSet<NState> visited)
    {
        while (!queue.empty()) {

            NState s = queue.pop();

            if (visited.contains(s))
                continue;

            visited.add(s);

            System.out.println(s);

            for (NEdge e : s.out) {
                System.out.println("  "+e);

                queue.push(e.destination);
            }
        }
    }

    /** Tokenize a file. This reads the whole file and calls
     * tokenize(String); see comments for that function. **/
    public ArrayList<Token<T>> tokenize(File f) throws IOException
    {
        BufferedReader ins = new BufferedReader(new FileReader(f));
        char c[] = new char[(int) f.length()];
        int len = ins.read(c);

        return tokenize(new String(c, 0, len));
    }

    /** Tokenize a string. You must add regular expressions
     * first. Once called, the expression will be compiled and will
     * not possible to add additional expressions. **/
    public ArrayList<Token<T>> tokenize(String s)
    {
        s = s + ((char) EOF_CHAR);

        if (!compiled)
            compile();

        ArrayList<Token<T>> tokens = new ArrayList<Token<T>>();

        if (s==null || s.length() == 0)
            return tokens;

        StringFeeder sf = new StringFeeder(s);

        // start parsing from the root.
        DState state = droot;
        StringBuilder sb = new StringBuilder();
        char c = sf.next();

        while (true) {

            // is there an outgoing edge for this input character?
            boolean consumed = false;
            for (DEdge e : state.out) {
                if (c >= e.c0 && c <= e.c1) {
                    consumed = true;
                    state = e.destination;
                    break;
                }
            }

            // there IS an edge, follow it.
            if (consumed) {

                sb.append(c);
                if (!sf.hasNext()) {
                    if (state.t != null || state.ignore) {
                        if (!state.ignore)
                            tokens.add(new Token<T>(state.t, sb.toString(), sf.line, sf.col));
                    } else {
                        // incomplete token means that we ran out of input
                        // but have not arrived in a state that produces a
                        // token. In other words, more input is needed to
                        // complete a token.
                        System.out.println("incomplete token in state "+state+" "+sb+" ");
                    }
                    return tokens;
                }
                c = sf.next();
                continue;
            }

            // There are NO matching edges. We must produce a token.

            // if we're still in the initial state, then we have not
            // consumed anything yet and there's a bad character in
            // the stream.  Create a token containing just the bad
            // character.
            if (state == droot) {
                sb.append(c);
                // Don't report bad characters. Users who want to do
                //   good error reporting should add a production for
                //   a single mismatched character.
                //
                if (showDeadEnds) {
                    System.out.println("GenericTokenizer: Got caught in a dead-end dstate!");
                    System.out.println("  dstate: "+state);
                    System.out.println("  input : "+escape(sb.toString()));
                }

                tokens.add(new Token(errorType, sb.toString(), sf.line, sf.col));

                if (!sf.hasNext())
                    return tokens;

                c = sf.next();
                sb = new StringBuilder();
                continue;
            }

            // We're not in the initial state; produce a token and
            // restart parsing from the root node.
            if (state.t != null && !state.ignore)
                tokens.add(new Token(state.t, sb.toString(), sf.line, sf.col));

            sb = new StringBuilder();
            state = droot;
        }
    }

    String findDeadStateInputSequence(int terminaldstate)
    {
        for (int depthlimit = 1; depthlimit < 10; depthlimit++) {
            String s = findDeadStateInputSequenceDLS(0, terminaldstate, depthlimit, 0);

            if (s != null)
                return s;
        }

        // couldn't find one
        return null;
    }

    // depth limited recursive search
    String findDeadStateInputSequenceDLS(int dstate, int terminaldstate, int depthlimit, int depth)
    {
        DState ds = dstates.get(dstate);
        if (ds.id == terminaldstate)
            return ""; // return non-null. we found it! We'll start building up the string

        if (depth > depthlimit)
            return null;

        for (DEdge de : ds.out) {
            String s = findDeadStateInputSequenceDLS(de.destination.id, terminaldstate, depthlimit, depth+1);
            if (s != null) {
                if (de.c0 == de.c1)
                    return escape(de.c0)+s;
                else
                    return String.format("[%s-%s]%s", escape(de.c0), escape(de.c1), s);
            }
        }

        return null;
    }

    //////////////////////////////////////////////////////////
    // Test/example code.
    public static void main(String args[])
    {
        GenericTokenizer<String> gt = new GenericTokenizer<String>("ERROR");
        /*	gt.add(" +", " +");
            gt.add("ab+", "ab+");
            gt.add("abc", "abc");
            gt.add("c*", "c*");
            gt.add("ddd|ee", "ddd|ee"); */
        //	gt.add("(a|b)+(c|d)?", "(a|b)+(c|d)?");
        //	gt.add("hh|i","hh|i");

        //	gt.add("(j)k","(j)k");

        gt.add("WHITE", "\\s+");
        gt.addIgnore("\\$");

        // do NOT add unary minus, it interferes with subtraction
        gt.add("NUMBER", "\\.[0-9]+((e|E)\\-?[0-9]+)?");
        gt.add("NUMBER", "[0-9]+[\\.0-9]*((e|E)\\-?[0-9]+)?");
        gt.add("SYMBOL", "[_A-Za-z][_A-Za-z0-9]*");
        gt.addEscape("OP", "++ -- <= == >= += -= *= /= && || << >> <<= >>= <<< >>> .* ./ .^ + - / * \\ ' [ ] ( ) { } ; < > , ^");

        gt.add("ERROR", ".");

        gt.dumpNroot();
        System.out.println("*****************");
        gt.dumpDroot();

        for (int i = 0; i < args.length; i++) {
            ArrayList<Token<String>> tokens = gt.tokenize(args[i]);
            for (int j = 0; j < tokens.size(); j++) {
                System.out.printf("%5d %-10s : '%s'\n", j, tokens.get(j).type, tokens.get(j).token);
            }
        }
    }
}
