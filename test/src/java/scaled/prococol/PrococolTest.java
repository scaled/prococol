//
// Scaled Prococol - a text-based protocol for communicating with sub-processes
// http://github.com/scaled/prococol/blob/master/LICENSE

package scaled.prococol;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.*;
import static org.junit.Assert.*;

public class PrococolTest {

    @Test public void testBasics () {
        StringWriter out = new StringWriter();
        Sender send = new Sender(out, true);
        final Map<String,String> sendData = new HashMap<>();
        sendData.put("string0", "We all live in a yellow submarine.");
        sendData.put("string1", "Mayonnaise is tasty.");
        sendData.put("text0", "Who was that man?\nThat's Kumar.");
        sendData.put("text1", "While I was going to St. Ives,\nI met a man with seven wives.");
        send.send("test0", sendData);
        send.send("test1", sendData);

        // System.out.println(out.toString());

        StringReader in = new StringReader(out.toString());
        int[] count = new int[] { 0 };
        Receiver recv = new Receiver(in, new Receiver.Listener() {
            public void onMessage (String name, Map<String,String> recvData) {
                assertEquals("test"+count[0], name);
                assertEquals(sendData, recvData);
                count[0] += 1;
            }
            public  void onIOFailure (IOException cause) {
                fail(cause.toString());
            }
            public void onUnexpected (Exception cause) {
                fail(cause.toString());
            }
        });
        recv.run();
        assertEquals(2, count[0]);
    }
}
