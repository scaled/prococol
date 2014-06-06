//
// Scaled Prococol - a text-based protocol for communicating with sub-processes
// http://github.com/scaled/prococol/blob/master/LICENSE

package scaled.prococol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * Receives prococol messages from an {@code InputStream}, decodes and dispatches them. This will
 * generally be constructed using {@link System#in} in the subprocess, or {@link
 * Process#getInputStream} in the parent process. The parent process will need to construct a
 * separate thread to drive the receiver; the child process may wish to drive its receiver on its
 * main thread or delegate it to a separate thread, depending on its own architecture.
 */
public class Receiver implements Runnable {

    /**
     * Handles notifications from a receiver. NOTE: listener methods are called on whatever thread
     * is executing {@link Receiver#run}; be sure to redirect this data to the appropriate thread
     * for processing.
     */
    public static interface Listener {
        /**
         * Called when a complete message is available.
         */
        void onMessage (String name, Map<String,String> data);

        /**
         * Called if a failure occurs when reading from our input stream. The session will be
         * terminated after reporting this error.
         */
        default void onIOFailure (IOException cause) {
            cause.printStackTrace(System.err);
        }

        /**
         * Called when we receive data that does not conform to the protocol. When non-conforming
         * data is seen, the receiver abandons the current message (potentially reporting multiple
         * errors as it ignores the remainder of the message) and resets to a state expecting the
         * next start of message frame.
         */
        default void onUnexpected (Exception error) {
            if (error instanceof ProtocolException) System.err.println(error.getMessage());
            else error.printStackTrace(System.err);
        }
    }

    /** Passed to {@link #onUnexpected} in the event of protocol errors. */
    public static class ProtocolException extends Exception {
        public ProtocolException (String msg) {
            super(msg);
        }
    }

    /**
     * Unescapes a line of text that is part of a {@code text} component.
     */
    public static String unescape (String line) {
        for (int ii = 0, ll = line.length(); ii < ll; ii++) {
            char c = line.charAt(ii);
            if (c == '\\') continue;
            else if (c == '%') return line.substring(ii);
            else return line;
        }
        return line;
    }

    /**
     * Creates a receiver that will read text from {@code in}.
     */
    public Receiver (InputStream in, Listener lner) throws UnsupportedEncodingException {
        this(new InputStreamReader(in, "UTF-8"), lner);
    }

    /**
     * Creates a receiver that will read text from {@code in}.
     */
    public Receiver (Reader in, Listener lner) {
        _reader = new BufferedReader(in);
        _lner = lner;
    }

    /**
     * Runs the "event loop" of this receiver until the input stream is closed.
     */
    public void run () {
        try {
            String line;
            while ((line = _reader.readLine()) != null) processLine(line);
        } catch (IOException ioe) {
            _lner.onIOFailure(ioe);
        }
    }

    protected void processLine (String line) {
        if (line.startsWith("%MSG ")) {
            if (_msgName != null) report("%MSG", "while processing %MSG", line);
            _msgName = line.substring(5);
            _keyName = null;
            _msgData = new HashMap<>();

        } else if (line.equals("%ENDMSG")) {
            if (reqmsg("%ENDMSG", line)) {
                try { _lner.onMessage(_msgName, _msgData); }
                catch (Exception e) { _lner.onUnexpected(e); }
                _msgName = null;
                _msgData = null;
                if (_keyName != null) report("%ENDMSG", "with dangling %KEY", line);
                _keyName = null;
                if (_text != null) report("%ENDMSG", "with dangling %TXT", line);
                _text = null;
            }

        } else if (line.startsWith("%KEY ")) {
            if (reqmsg("%KEY", line)) {
                if (_keyName != null) report("%KEY", "while processing %KEY", line);
                _keyName = line.substring(5);
            }

        } else if (line.startsWith("%STR ")) {
            if (reqmsg("%STR", line) && reqkey("%STR", line)) {
                _msgData.put(_keyName, line.substring(5));
                _keyName = null;
            }

        } else if (line.equals("%TXT")) {
            if (reqmsg("%TXT", line) && reqkey("%TXT", line)) {
                if (_text != null) report("%TXT", "while processing %TXT", line);
                _text = new StringBuilder();
            }

        } else if (line.equals("%ENDTXT")) {
            if (reqmsg("%TXT", line) && reqkey("%TXT", line) && reqtxt("%ENDTXT", line)) {
                _msgData.put(_keyName, _text.toString());
                _keyName = null;
                _text = null;
            }

        } else {
            if (reqmsg("text", line) && reqkey("text", line) && reqtxt("text", line)) {
                // TODO: this doesn't preserve leading blank lines; do we care?
                if (_text.length() > 0) _text.append(LINE_SEP);
                _text.append(unescape(line));
            }
        }
    }

    protected boolean reqmsg (String kind, String line) {
      return req(_msgName, "outside of %MSG", kind, line); }
    protected boolean reqkey (String kind, String line) {
      return req(_keyName, "with no %KEY", kind, line); }
    protected boolean reqtxt (String kind, String line) {
      return req(_text, "with no %TXT", kind, line); }
    protected boolean req (Object sentinel, String msg, String kind, String line) {
        if (sentinel != null) return true;
        report(kind, msg, line);
        return false;
    }
    protected void report (String rkind, String msg, String line) {
        _lner.onUnexpected(new ProtocolException("Received "+ rkind +" "+ msg +": "+ line));
    }

    private static final String LINE_SEP = System.getProperty("line.separator");

    private final BufferedReader _reader;
    private final Listener _lner;

    private String _msgName;
    private Map<String,String> _msgData;
    private String _keyName;
    private StringBuilder _text;
}
