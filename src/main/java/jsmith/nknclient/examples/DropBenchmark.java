package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.Identity;
import jsmith.nknclient.client.NKNClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 */
public class DropBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(DropBenchmark.class);

    public static void main(String[] args) throws InterruptedException {
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

            final Identity identityA = new Identity("Test." + test + ".A");
            final Identity identityB = new Identity("Test." + test + ".B");

            boolean received[] = {false};

            final NKNClient clientA = new NKNClient(identityA)
                    .start();
            final NKNClient clientB = new NKNClient(identityB)
                    .onSimpleMessage((from, message) -> {
                        received[0] = true;
                    })
                    .start();

            clientA.sendSimpleMessage(identityB.getFullIdentifier(), "Message");

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
