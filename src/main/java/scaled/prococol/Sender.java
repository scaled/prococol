//
// Scaled Prococol - a text-based protocol for communicating with sub-processes
// http://github.com/scaled/prococol/blob/master/LICENSE

package scaled.prococol;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;

/**
 * Encodes and sends prococol messages to an {@code OutputStream}. This will generally be
 * constructed with {@link System#out} in the subprocess, or {@link Process#getInputStream} in the
 * parent process. Sender does nothing to protect the parent process from blocking if the child
 * process becomes unresponsive and the output buffer fills up. Paranoid parents should do something
 * like forking a separate thread which pops messages from a queue and writes them to a sender.
 */
public class Sender {

    /**
     * Escapes a line of text for use in a {@code text} component. If the line starts with zero or
     * more {@code \} characters and a {@code %} character, the line will have another {@code \}
     * character prepended to it. See {@link Receiver#unescape} for the inverse operation.
     */
    public static String escape (String line) {
        for (int ii = 0, ll = line.length(); ii < ll; ii++) {
            char c = line.charAt(ii);
            if (c == '\\') continue;
            else if (c == '%') return "\"" + line;
            else return line;
        }
        return line;
    }

    /**
     * Creates a sender which will write output to {@code out}.
     *
     * @param strict if true, the sender will throw {@link IllegalStateException} when it detects
     * invalid protocol sequences. If false, it just does what you ask without complaint. The
     * receiver is somewhat robust in the fact of invalid protocol sequences, so non-strict
     * operation is not a completely crazy idea in situations where you don't have total control
     * over the code that structures your messages.
     */
    public Sender (OutputStream out, boolean strict) throws UnsupportedEncodingException {
        this(new OutputStreamWriter(out, "UTF-8"), strict);
    }

    /**
     * See {@link Sender(OutputStream,boolean)}.
     */
    public Sender (Writer out, boolean strict) {
        _strict = strict;
        _out = new PrintWriter(new BufferedWriter(out));
    }

    /**
     * Sends a complete message to our receiver.
     */
    public void send (String msgName, Map<String,String> msgData) {
        startMessage(msgName);
        try {
            for (Map.Entry<String,String> entry : msgData.entrySet()) {
                String key = entry.getKey(), data = entry.getValue();
                if (data == null) System.err.println(
                    "Dropping null messge param [msg=" + msgName + ", key=" + key + "]");
                else if (data.contains(LINE_SEP)) sendText(key, data);
                else sendString(key, data);
            }
        } finally {
            endMessage();
        }
    }

    /**
     * Starts a new message. This must be matched by a call to {@link #endMessage}.
     */
    public void startMessage (String msgName) {
        reqstate(_msgName == null, "startMessage() called with message in progress");
        reqnosep(msgName, "Message name");
        _msgName = msgName;
        _out.print("%MSG ");
        _out.println(msgName);
    }

    /**
     * Ends the current message. This must have been preceded by a call to {@link #startMessage},
     * and presumably by one or more calls to {@link #sendString}, {@link #sendText}, etc.
     */
    public void endMessage () {
        reqstate(_msgName != null, "endMessage() called with no message in progress");
        _out.println("%ENDMSG");
        _out.flush();
        _msgName = null;
    }

    /**
     * Sends {@code key} and {@code value} as part of the current message. {@code value} must not
     * contain line separators. Use {@link #sendText} to send multiline text.
     */
    public void sendString (String key, String value) {
        reqstate(_msgName != null, "sendString() called with no message in progress");
        reqnosep(key, "Key");
        reqnosep(value, "String payload");
        _out.print("%KEY ");
        _out.println(key);
        _out.print("%STR ");
        _out.println(value);
    }

    /**
     * Sends {@code key} and {@code text} as part of the current message. {@link text} may contain
     * line separators. Use {@link #setString} if the text is single line.
     */
    public void sendText (String key, String text) {
        reqstate(_msgName != null, "sendString() called with no message in progress");
        reqnosep(key, "Key");
        startText(key);
        _out.println(text);
        endText();
    }

    /**
     * Starts a {@code text} component with key {@code key} in the current message. This must be
     * followed by zero or more calls to {@link #sendTextLine} then one call to {@link #endText}.
     */
    public void startText (String key) {
        reqstate(_msgName != null, "startText() called with no message in progress");
        reqstate(!_inText, "startText() called while existing text in progress");
        _out.print("%KEY ");
        _out.println(key);
        _out.println("%TXT");
        _inText = true;
    }

    /**
     * Adds {@code text} and a trailing line separator to the currently accumulating {@code text}
     * component. This must have been preceded by a call to {@link #startText}.
     */
    public void sendTextLine (String text) {
        reqstate(_msgName != null, "sendTextLine() called with no message in progress");
        reqstate(_inText, "sendTextLine() called with no text in progress");
        _out.println(escape(text));
    }

    /**
     * Returns a {@link PrintWriter} that can be used to write the contents of a {@code text}
     * component.
     *
     * <p>The caller must ensure that the written text does not conflict with the Prococol protocol.
     * The safest way is to ensure that {@link #escape}'s conditions are met for each line of text
     * written (i.e. % is prefixed by a backslash if it appears at the start of any line, etc.).
     * However, it is also possible to include unescaped {@code %}s in the text output as long as
     * the specific tokens used by Prococol do not appear at the start of any line (%MSG, %ENDMSG,
     * %KEY, %STR, %TXT, %ENDTXT). Balance risk and convenience as you wish.</p>
     *
     * <p>Every line of text written to this writer must be followed by a line separator. If you
     * write a dangling line and then call {@link #endText}, your message will be hosed.</p>
     */
    public PrintWriter textWriter () {
        return _out;
    }

    /**
     * Ends a currently accumulating {@code text} component. This must have been preceded by a call
     * to {@link #startText}.
     */
    public void endText () {
        reqstate(_msgName != null, "endText() called with no message in progress");
        reqstate(_inText, "endText() called with no text in progress");
        _out.println("%ENDTXT");
        _inText = false;
    }

    protected void reqstate (boolean cond, String message) {
        if (_strict && !cond) throw new IllegalStateException(message);
    }

    protected void reqnosep (String string, String name) {
        if (_strict && string.contains(LINE_SEP)) throw new IllegalArgumentException(
            name + " must not contain line separator");
    }

    private static final String LINE_SEP = System.getProperty("line.separator");

    private final boolean _strict;
    private PrintWriter _out;
    private String _msgName;
    private boolean _inText;
}
