package april.procman;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.*;
import javax.swing.text.*;

import lcm.lcm.*;
import april.lcmtypes.*;

import april.util.*;

/**
 * Issues:
 * 1. Auto-scrolling is in a funny state, and must be done manually
 * because it is hard to find out if the pane isn't at the end because
 * a user wants to scroll to the middle, or because new content was
 * added. Particularly, adding output to a Document doesnt seem to be
 * reflected immediately in the JScrollBar, so before adding input, if
 * the view is not at the end, it may be because of content which was
 * added last time, yet we didn't find out about it soon enough to
 * update the run that time, vs. if the change was due to the user.
 * 2. Document styling changes appear to occur when a buffer becomes
 * the selected one.  -JHS
 */
class Spy implements LCMSubscriber, AdjustmentListener
{
    public static int MIN_H_BOTTOM = 300;
    public static int MIN_H_TOP = 100;
    public static int MIN_WIDTH = 600;


    public static final int WIN_WIDTH = 1024;
    public static final int WIN_HEIGHT = 800;
    public static final int HOST_WIDTH = 200;

    JFrame    jf;
    JTable    proctable, hosttable;

    JTabbedPane tabPane;
    JTextPane textError;
    JScrollPane textErrorScroll;

    JButton   startSelectedButton, stopSelectedButton;
    JButton   clearSelectedButton, clearAllButton, resetButton;
    JCheckBox autoScrollBox;

    ProcGUIDocument outputSummary = new ProcGUIDocument();

    ProcMan proc;

    ArrayList<ProcRecordG> processes;
    HashMap<Integer, ProcRecordG> processesMap;
    ArrayList<HostRecord> hosts = new ArrayList<HostRecord>();

    static LCM lcm = LCM.getSingleton();

    ProcessTableModel processTableModel = new ProcessTableModel();
    HostTableModel hostTableModel = new HostTableModel();

    boolean scrollToEnd;

    Spy(ProcMan _proc)
    {
        proc = _proc;
        init();
    }

    Spy()
    {
        proc = null;
        init();
    }

    public void init()
    {
        processes = new ArrayList<ProcRecordG>();
        processesMap = new HashMap<Integer, ProcRecordG>();

        proctable = new JTable(processTableModel);
        TableRowSorter sorter = new TableRowSorter(processTableModel);
        proctable.setRowSorter(sorter);
        sorter.toggleSortOrder(1);

        // allow section of processes via mouse or keyboard (up and down) keys
        ListSelectionModel rowSM = proctable.getSelectionModel();
        rowSM.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    if (e.getValueIsAdjusting())
                        return;
                    updateTableSelection();
                }
            });

        hosttable = new JTable(hostTableModel);
        hosttable.setRowSorter(new TableRowSorter(hostTableModel));

        textError = new JTextPane();
        textError.setEditable(false);
        textError.setDocument(outputSummary);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1,5));
        startSelectedButton = new JButton("Start Selected");
        stopSelectedButton = new JButton("Stop Selected");
        clearSelectedButton = new JButton("Clear Sel. Output");
        clearAllButton = new JButton("Clear All Output");
        resetButton = new JButton("Reset");
        scrollToEnd = true;
        autoScrollBox = new JCheckBox("Auto Scroll", scrollToEnd);

        startSelectedButton.setEnabled(false);
        stopSelectedButton.setEnabled(false);

        buttonPanel.add(startSelectedButton);
        buttonPanel.add(stopSelectedButton);
        buttonPanel.add(clearSelectedButton);
        buttonPanel.add(clearAllButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(autoScrollBox);

        int sizeTopPane = (int)(0.25*WIN_HEIGHT);

        if (proc != null) {
            startSelectedButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int []rows = proctable.getSelectedRows();
                        for (int i = 0; i < rows.length; i++) {
                            rows[i] = proctable.convertRowIndexToModel(rows[i]);
                            proc.setRunStatus(processes.get(rows[i]).procid, true);
                        }
                        updateStartStopText();
                    }
                });
            stopSelectedButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int []rows = proctable.getSelectedRows();
                        for (int i = 0; i < rows.length; i++) {
                            rows[i] = proctable.convertRowIndexToModel(rows[i]);
                            proc.setRunStatus(processes.get(rows[i]).procid, false);
                        }
                        updateStartStopText();
                    }
                });

            // add 1 for title and another for additoinal whitespace below table in pane
            sizeTopPane = (2 + proc.processes.size()) * (proctable.getRowHeight() +
                                                         proctable.getRowMargin());
        }

        clearSelectedButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    synchronized(Spy.this)
                    {
                        clearSelected();
                    }
                }
            });

        clearAllButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    synchronized(Spy.this)
                    {
                        clearAll();
                    }
                }
            });

        resetButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    synchronized(Spy.this)
                    {
                        reset();
                    }
                }
            });

        autoScrollBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    scrollToEnd = !scrollToEnd;
                }
            });


        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                        new JScrollPane(proctable),
                                        new JScrollPane(hosttable));
        jsp.setDividerLocation(WIN_WIDTH - HOST_WIDTH);
        jsp.setResizeWeight(1);

        JPanel jp = new JPanel();
        jp.setLayout(new BorderLayout());
        jp.add(jsp, BorderLayout.CENTER);
        jp.add(buttonPanel, BorderLayout.SOUTH);

        textErrorScroll = new JScrollPane(textError);
        textErrorScroll.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener(){
                public void adjustmentValueChanged(AdjustmentEvent e){
                    if (scrollToEnd) {
                        JScrollBar jsb = textErrorScroll.getVerticalScrollBar();
                        jsb.setValue(jsb.getMaximum());
                    }
                }
            });

        tabPane = new JTabbedPane();

        JSplitPane textJsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, textErrorScroll, tabPane);

        JSplitPane mainJsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jp, textJsp);

        jp.setMinimumSize(new Dimension(MIN_WIDTH, MIN_H_TOP));
        textErrorScroll.setMinimumSize(new Dimension(MIN_WIDTH / 2, MIN_H_BOTTOM));
        tabPane.setMinimumSize(new Dimension(MIN_WIDTH / 2, MIN_H_BOTTOM));

        jf = new JFrame("ProcMan Spy " + (proc == null ? "(Read Only)" : "(Privileged)"));
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        MIN_WIDTH += 100;
        int MIN_HEIGHT = MIN_H_BOTTOM + MIN_H_TOP + 100;
        jf.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        jf.setLayout(new BorderLayout());
        jf.add(mainJsp, BorderLayout.CENTER);
        jf.setSize(WIN_WIDTH, WIN_HEIGHT);
        jf.setVisible(true);


        sizeTopPane += buttonPanel.getHeight();
        sizeTopPane = Math.min(sizeTopPane, WIN_HEIGHT - MIN_H_BOTTOM);

        textJsp.setDividerLocation(500); // Size must get set after setVisible(true)
        mainJsp.setDividerLocation(sizeTopPane);


        Action deselect = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    proctable.clearSelection();
                }
            };

        Action selectAll = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    proctable.selectAll();
                }
            };

        mainJsp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("A"),
                                                                   "selectAll");
        mainJsp.getActionMap().put("selectAll", selectAll);

        mainJsp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
                                                                   "deselect");
        mainJsp.getActionMap().put("deselect", deselect);

        TableColumnModel tcm = proctable.getColumnModel();
        tcm.getColumn(0).setPreferredWidth(50);
        tcm.getColumn(1).setPreferredWidth(200);
        tcm.getColumn(2).setPreferredWidth(500);
        tcm.getColumn(3).setPreferredWidth(100);
        tcm.getColumn(4).setPreferredWidth(120);
        tcm.getColumn(5).setPreferredWidth(80);

        lcm.subscribe("PROCMAN_STATUS_LIST", this);
        lcm.subscribe("PROCMAN_OUTPUT", this);
        lcm.subscribe("PROCMAN_PROCESS_LIST", this);
    }

    public synchronized void updateTableSelection()
    {
        int []rows = proctable.getSelectedRows();

        // Stay on current tab if in new selection list
        JScrollPane curPane = (JScrollPane)tabPane.getSelectedComponent();

        tabPane.removeAll();
        for (int i = 0; i < rows.length; i++) {
            int row = proctable.convertRowIndexToModel(rows[i]);
            ProcRecordG pr = processes.get(row);
            tabPane.add(String.format("%d-%.10s", pr.procid, pr.name), pr.outputPane);

            if (scrollToEnd) {
                JScrollBar jsb = pr.outputPane.getVerticalScrollBar();
                jsb.setValue(jsb.getMaximum());
            }

            if (curPane == pr.outputPane)
                tabPane.setSelectedComponent(curPane);

            if (i < 9)
                tabPane.setMnemonicAt(i, KeyEvent.VK_1 + i);
        }
        updateStartStopText();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(lcm, channel, ins);
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    public void messageReceivedEx(LCM lcm, String channel, LCMDataInputStream ins) throws IOException
    {
        if (channel.equals("PROCMAN_OUTPUT")) {
            procman_output_t po = new procman_output_t(ins);

            ProcRecordG pr = ensureProcRecord(po.procid); // processesMap.get(po.procid);

            if (pr == null)
                return;

            if (po.stream == 0) {
                pr.output.appendDefault(po.data + "\n");
            } else {
                pr.output.appendError(po.data + "\n");
                outputSummary.appendError(String.format("[%2d-%8.8s] %s\n",
                                                        pr.procid, pr.name, po.data));
            }
        }else if (channel.equals("PROCMAN_STATUS_LIST")) {
            procman_status_list_t psl = new procman_status_list_t(ins);

            /////////// Update Process Statistics
            for (int i = 0; i < psl.nprocs; i++) {

                procman_status_t ps = psl.statuses[i];

                ProcRecordG pr = ensureProcRecord(ps.procid); // processesMap.get(ps.procid);
                if (pr == null) {
                    System.out.println("unknown procid "+ps.procid);
                    continue;
                }

                pr.lastStatus = ps;
                pr.restartCount = ps.restarts;
                pr.lastStatusUtime = psl.utime;

                processTableModel.fireTableRowsUpdated(pr.pridx, pr.pridx);
                updateStartStopText();
            }

            ////////// Update Host Statistics
            HostRecord hr = ensureHost(psl.host);
            hr.skew = psl.utime - TimeUtil.utime();
            hr.rtt = TimeUtil.utime() - psl.received_utime;

            hostTableModel.fireTableCellUpdated(hr.hridx,1);
            hostTableModel.fireTableCellUpdated(hr.hridx,2);
        } else if(channel.equals("PROCMAN_PROCESS_LIST")){
            procman_process_list_t proc_list = new procman_process_list_t(ins);
            for(int i = 0; i < proc_list.nprocs; i++){
                procman_process_t p = proc_list.processes[i];
                ProcRecordG pr = ensureProcRecord(p.procid);
                pr.cmdline = p.cmdline;
                pr.host = p.host;
                pr.name = p.name;
                pr.cmdRunning = (p.running ? RunStatus.RUNNING :
                                 RunStatus.STOPPED);
                processTableModel.fireTableRowsUpdated(pr.pridx, pr.pridx);
            }
        }
    }


    class HostRecord
    {
        String host;

        // how long did it take for it to reply to our last send
        // command? (usecs)
        long   rtt;

        // what is the difference between the utime on dameon and
        // master?  (includes latency) (usecs)
        long   skew;

        int hridx;
    }


    class ProcessTableModel extends AbstractTableModel
    {
        String columnNames[] = { "ProcID", "Name", "Command", "Host", "Daemon" , "Controller" };

        public int getColumnCount()
        {
            return columnNames.length;
        }

        public String getColumnName(int col)
        {
            return columnNames[col];
        }

        public int getRowCount()
        {
            return processes.size();
        }

        public Object getValueAt(int row, int col)
        {
            ProcRecordG pr = processes.get(row);

            switch (col) {
                case 0:
                    return pr.procid;
                case 1:
                    return pr.name;
                case 2:
                    return pr.cmdline;
                case 3:
                    return pr.host;
                case 4:
                    if (pr.lastStatus == null)
                        return "Unknown";
                    else {
                        int exitCode = (pr.lastStatus != null) ? pr.lastStatus.last_exit_code : 0;
                        return pr.lastStatus.running ? "Running" : "Stopped ("+exitCode+")";
                    }
                case 5:
                    switch(pr.cmdRunning) {
                        case RUNNING:
                            return "Running";
                        case STOPPED:
                            return "Stopped";
                        default:
                            return "Unknown";
                    }
            }

            return "??";
        }
    }

    class HostTableModel extends AbstractTableModel
    {
        String columnNames[] = { "Host", "RTT", "Skew" };

        public int getColumnCount()
        {
            return columnNames.length;
        }

        public String getColumnName(int col)
        {
            return columnNames[col];
        }

        public int getRowCount()
        {
            return hosts.size();
        }

        public Object getValueAt(int row, int col)
        {
            HostRecord hr = hosts.get(row);

            switch (col) {
                case 0:
                    return hr.host;
                case 1:
                    return String.format("%.1f ms", hr.rtt/1000.0);
                case 2:
                    return String.format("%.1f ms", hr.skew/1000.0);
            }
            return "??";
        }
    }

    synchronized void clearSelected()
    {
        int []rows = proctable.getSelectedRows();
        for (int i = 0; i < rows.length; i++) {
            rows[i] = proctable.convertRowIndexToModel(rows[i]);
            processes.get(rows[i]).clearOutputPane();
        }
        updateTableSelection();
    }

    synchronized void clearAll()
    {
        for (ProcRecordG rec : processes)
            rec.clearOutputPane();

        // insert blank line on summary output pane
        outputSummary.appendError("\n");

        updateTableSelection();
    }

    synchronized void reset()
    {
        // XXX need to fire some events here...
        int removedProc  = processes.size();
        if (removedProc == 0)
            return;
        processes.clear();
        processesMap.clear();

        int removedHost  = hosts.size();
        hosts.clear();

        processTableModel.fireTableRowsDeleted(0,removedProc - 1);
        if (removedHost > 0)
            hostTableModel.fireTableRowsDeleted(0,removedHost - 1);
    }

    synchronized ProcRecordG ensureProcRecord(int procid)
    {
        ProcRecordG pr = processesMap.get(procid);
        if (pr != null)
            return pr;

        // otherwise, we've got some data to create and fill in
        pr = new ProcRecordG();
        pr.procid = procid;
        pr.cmdline = "???";
        pr.host = "???";
        pr.name = "???";
        pr.pridx = processes.size();
        pr.cmdRunning = RunStatus.UNKNOWN;

        // Add process to relevant structures
        processes.add(pr);
        processesMap.put(procid, pr);
        processTableModel.fireTableRowsInserted(processes.size() - 1,
                                                processes.size() - 1);

        return pr;
    }

    synchronized HostRecord ensureHost(String hostStr)
    {
        for (HostRecord host : hosts)
            if (host.host.equals(hostStr))
                return host;

        HostRecord hr = new HostRecord();
        hr.host = hostStr;
        hr.hridx = hosts.size();
        hosts.add(hr);
        hostTableModel.fireTableRowsInserted(hr.hridx, hr.hridx);
        return hr;
    }

    /**
     * This method should ideally be called whenever the GUI state of
     * running is changed for a process in row 'row'
     */
    void updateStartStopText()
    {
        if (proc == null)
            return;

        // check all selected rows
        int []rows = proctable.getSelectedRows();
        int numRunning = 0;
        int numStopped = 0;

        for (int i = 0; i < rows.length; i++) {
            int procid =  processes.get(proctable.convertRowIndexToModel(rows[i])).procid;
            if (proc.getRunStatus(procid))
                numRunning++;
            else
                numStopped++;
        }
        // if all selected processes are running then disable setRunning button
        startSelectedButton.setEnabled(numRunning < rows.length);

        // if all selected processes are stopped then disable setStopped button
        stopSelectedButton.setEnabled(numStopped < rows.length);
    }


    /** Handle scroll events for tabPane **/
    public void adjustmentValueChanged(AdjustmentEvent e)
    {
        if (scrollToEnd) {
            JScrollBar jsb = ((JScrollPane)tabPane.getSelectedComponent()).getVerticalScrollBar();
            jsb.setValue(jsb.getMaximum());
        }
    }

    public static enum RunStatus {RUNNING, STOPPED, UNKNOWN};

    class ProcRecordG extends ProcRecord
    {
        ProcGUIDocument output;
        JScrollPane outputPane;   // create output pane once and toggle showing in tab
        int pridx;

        procman_status_t lastStatus;
        long lastStatusUtime;


        RunStatus cmdRunning;   // commanded running state from controller (different from status)

        // was process running on last status message.  Used to handle exit code = 0
        boolean daemonIsRunning;

        ProcRecordG()
        {
            output = new ProcGUIDocument();

            // create output scroll pane once
            JTextPane text = new JTextPane();
            text.setEditable(false);
            text.setDocument(output);

            outputPane = new JScrollPane(text);
            outputPane.getVerticalScrollBar().addAdjustmentListener(Spy.this);
        }

        void clearOutputPane()
        {
            output = new ProcGUIDocument();

            ((JTextPane)outputPane.getViewport().getView()).setDocument(output);
        }
    }

    class ProcGUIDocument extends DefaultStyledDocument
    {
        Style defaultStyle, errorStyle, summaryStyle;

        static final int MAX_LENGTH = 128*1024;

        ProcGUIDocument()
        {
            defaultStyle = getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setFontFamily(defaultStyle, "Monospaced");
            StyleConstants.setFontSize(defaultStyle, 10);

            errorStyle   = addStyle("ERROR", defaultStyle);
            StyleConstants.setFontFamily(errorStyle, "Monospaced");
            StyleConstants.setFontSize(errorStyle, 10);
            StyleConstants.setForeground(errorStyle, Color.red);

            summaryStyle = addStyle("SUMMARY", defaultStyle);
            StyleConstants.setFontFamily(summaryStyle, "Monospaced");
            StyleConstants.setFontSize(summaryStyle, 10);
            StyleConstants.setForeground(summaryStyle, Color.blue);
        }

        void insertStringEx(int pos, String s, Style style)
        {
            // avoid synchrony with UpdateTableSelection, which causes an exception.
            synchronized(Spy.this) {

                try {
                    if (getLength() > MAX_LENGTH) {
                        remove(0, MAX_LENGTH / 10);
                    }

                    insertString(getLength(), s, style);
                } catch (Exception ex) {
                    System.out.print("caught: ");
                    ex.printStackTrace();
                }
            }
        }

        void appendDefault(String s)
        {
            insertStringEx(getLength(), s, defaultStyle);
        }

        void appendError(String s)
        {
            insertStringEx(getLength(), s, errorStyle);
        }

        void appendSummary(String s)
        {
            insertStringEx(getLength(), s, summaryStyle);
        }

    }

    public static void main(String args[])
    {
        Spy pg = new Spy();
    }

}
