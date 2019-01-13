package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.Identity;
import jsmith.nknclient.client.NKNClient;
import jsmith.nknclient.client.NKNClientException;
import jsmith.nknclient.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 */
public class DropBenchmarkEx {

    private static final Logger LOG = LoggerFactory.getLogger(DropBenchmarkEx.class);

    public static void main(String[] args) throws InterruptedException, NKNClientException {
        TPLogger.DEBUG();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();


        final int timeout = 5000;
        int dropCount = 0;
        int tries = 100;

        for (int test = 0; test < tries; test++) {

            final Identity identityA = new Identity("Test." + test + ".A", Wallet.createNew());
            final Identity identityB = new Identity("Test." + test + ".B", Wallet.createNew());

            boolean received[] = {false};

            final NKNClient clientA = new NKNClient(identityA)
                    .start();
            final NKNClient clientB = new NKNClient(identityB)
                    .onNewMessage(receivedMessage -> received[0] = true)
                    .start();

            clientA.sendTextMessageAsync(identityB.getFullIdentifier(), null, "Message");

            Thread.sleep(timeout);

            clientA.close();
            clientB.close();

            if (!received[0]) {
                dropCount ++;
                LOG.error("Message has been dropped! Test: {}", test);
            }

        }

        if (dropCount == 0) {
            LOG.info("Out of {} tries, not a single message was dropped", tries);
        } else {
            LOG.error("Out of {} tries, {} messages have been dropped", tries, dropCount);
        }

    }

}
