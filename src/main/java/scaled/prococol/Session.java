//
// Scaled Prococol - a text-based protocol for communicating with sub-processes
// http://github.com/scaled/prococol/blob/master/LICENSE

package scaled.prococol;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Simplifies the process of communicating with a subprocess as a series of request/response pairs.
 */
public class Session implements AutoCloseable {

    /** The subprocess with which we're communicating. */
    public final SubProcess proc;

    /** Used by {@link #interact}. */
    public static interface Interactor {
        /**
         * Called when a message is received in response to an interaction.
         * @return true if the interaction is finished, false if further messages are expected
         */
        boolean onMessage (String msgName, Map<String,String> msgData);
    }

    /**
     * Creates a session with a simple subprocess. See {@link Session(Executor,SubProcess.Config)}
     * for full docs.
     */
    public Session (Executor exec, String... command) throws IOException {
        this(exec, SubProcess.config(command));
    }

    /**
     * Creates a session with the specified subprocess configuration.
     *
     * @param exec an executor on which to dispatch all callbacks. This executor must not run
     * operations concurrently.
     */
    public Session (Executor exec, SubProcess.Config config) throws IOException {
        proc = new SubProcess(config, new SubProcess.Listener() {
            public void onMessage (String name, Map<String,String> data) {
                if (_actor != null) exec.execute(() -> {
                    if (_actor.onMessage(name, data)) _actor = null;
                });
                else onErrorOutput("Message received outside of interaction [name=%s, data=%s]".
                                   format(name, data));
            }
            public void onIOFailure (IOException cause) {
                onUnexpected(cause);
            }
            public void onUnexpected (Exception error) {
                StringWriter out = new StringWriter();
                error.printStackTrace(new PrintWriter(out));
                onErrorOutput(out.toString());
            }
            public void onErrorOutput (String line) {
                exec.execute(() -> Session.this.onErrorOutput(line));
            }
        });
    }

    /**
     * Closes our subprocess, terminating this session.
     */
    public void close () throws IOException {
        proc.close();
    }

    /**
     * Returns true if there's an interaction in progress.
     */
    public boolean interacting () {
        return _actor != null;
    }

    /**
     * Initiates an interaction in this session. {@code msgName} and {@code msgData} will be sent as
     * a message to the other party in this session. {@code interactor} will be informed of all
     * messages received from the other party until it indicates that the interaction is complete.
     *
     * @throws IllegalStateException if an interaction is already in progress.
     */
    public void interact (String msgName, Map<String,String> msgData, Interactor interactor) {
        if (_actor != null) throw new IllegalStateException("Interaction already in progress.");
        _actor = interactor;
        proc.sender.send(msgName, msgData);
    }

    /**
     * Called for all out-of-band communication from the process. This includes the process's stderr
     * output, as well as reports of any I/O or or protocol failures. Defaults to writing output to
     * stderr. This is called via the executor and thus on a "safe" thread.
     */
    protected void onErrorOutput (String text) {
        System.err.println(text);
    }

    // this is only updated on the executor thread, but it's read by an I/O thread
    private transient Interactor _actor;
}
