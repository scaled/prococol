//
// Scaled Prococol - a text-based protocol for communicating with sub-processes
// http://github.com/scaled/prococol/blob/master/LICENSE

package scaled.prococol;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplifies the process of communicating with a subprocess using Prococol. The subprocess is
 * expected to create a {@code Receiver} on its own stdin, and a {@code Sender} on stdout. Anything
 * the subprocess sends to stderr will be captured and reported via {@link #onErrorOutput}.
 */
public class SubProcess implements AutoCloseable {

  /** The sender that can be used to communicate with this subprocess. */
  public final Sender sender;

  /** Configures a subprocess. */
  public static interface Config {
    /** The command and arguments used to launch the subprocess. */
    String[] command ();

    /** Indicates whether our sender should be strict. See {@link Sender}. */
    default boolean strictSender () {
      return false;
    }

    /** Returns the envvars to set in the subprocess. Defaults to none. */
    default Map<String, String> environment () {
      return new HashMap<>();
    }

    /** Returns the {@code cwd} in which to exec the subprocess. Defaults to this process's cwd. */
    default File cwd () {
      return new File(System.getProperty("user.dir"));
    }

    /** If true, debug information will be sent to {@link Listener#onErrorOutput}. */
    default boolean debug () {
      return false;
    }
  }

  /** Extends {@link Receiver.Listener} with subprocess bits. */
  public interface Listener extends Receiver.Listener {
    /**
     * Called when a line is read from the subprocess's stderr. The default implementation is to
     * write the line to our our stderr. NOTE: this method is called directly from the stderr
     * reader thread. Be sure to pass the data to the appropriate thread for processing.
     */
    default void onErrorOutput (String line) {
      System.err.println(line);
    }
  }

  /**
   * Returns a config for command with no custom environment, working directory, etc.
   */
  public static Config config (String... command) {
    return new Config() {
      public String[] command  () { return command; }
    };
  }

  /**
   * Creates and starts a subprocess. If this constructor completes without exception, the
   * subprocess will have been started.
   *
   * @param lner the listener that will handle commands received by the subprocess.
   * @param config the configuration for the subprocess.
   * @throws IOException if any error occurs starting the subprocess.
   */
  public SubProcess (Config config, Listener lner) throws IOException {
    // first start our process, if this fails, everything else will be aborted
    String[] cmd = config.command();
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(config.cwd());
    pb.environment().putAll(config.environment());

    if (config.debug()) {
      lner.onErrorOutput("Starting sub-process:");
      lner.onErrorOutput("  Command: " + cmd[0]);
      for (int ii = 1; ii < cmd.length; ii++) lner.onErrorOutput("  Arg: " + cmd[ii]);
      lner.onErrorOutput("  CWD: " + config.cwd());
      Map<String,String> env = config.environment();
      if (!env.isEmpty()) {
        lner.onErrorOutput("  Env:");
        for (Map.Entry<String,String> entry : env.entrySet()) lner.onErrorOutput(
          "    " + entry.getKey() + " -> " + entry.getValue());
      }
    }
    _config = config;
    _name = cmd[0];
    _lner = lner;
    _proc = pb.start();

    // create our sender and receiver
    sender = new Sender(_proc.getOutputStream(), config.strictSender());

    // start a thread to drive our receiver
    Thread stdin = new Thread(new Receiver(_proc.getInputStream(), lner), "Subproc: stdin");
    stdin.setDaemon(true);
    stdin.start();

    // start a thread to read stderr and pass it along
    Thread stderr = new Thread("Subproc: stderr") {
      public void run () {
        try {
          BufferedReader bin = new BufferedReader(
            new InputStreamReader(_proc.getErrorStream(), "UTF-8"));
          String line;
          while ((line = bin.readLine()) != null) {
            lner.onErrorOutput(line);
          }
        } catch (IOException ioe) {
          lner.onIOFailure(ioe);
        }
      }
    };
    stderr.setDaemon(true);
    stderr.start();
  }

  /**
   * Closes the subprocess's output stream. This may trigger termination if it expects that sort
   * of thing.
   */
  public void close () throws IOException {
    if (_proc.isAlive()) {
      if (_config.debug()) _lner.onErrorOutput(_name + ": Closing stdin.");
      _proc.getOutputStream().close();
    }
  }

  /**
   * Terminates the subprocess forcibly.
   */
  public void kill () {
    if (_config.debug()) _lner.onErrorOutput(_name + ": Killing subproc.");
    _proc.destroyForcibly();
  }

  /**
   * Waits for the subprocess to complete and returns its exit code.
   */
  public int waitFor () throws InterruptedException {
    return _proc.waitFor();
  }

  @Override public String toString () {
    return _proc.toString();
  }

  private final Config _config;
  private final String _name;
  private final Listener _lner;
  private final Process _proc;
}
