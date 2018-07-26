package jsmith.nknclient.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import jsmith.nknclient.client.NKNClient;
import jsmith.nknclient.client.Identity;

import java.io.File;

/**
 *
 */
public class Simple {

    public static void main(String[] args) {
        TPLogger.DEBUG();
        TPLogger.setLogFunction(
                new LogFunctionMultiplexer(
                        LogFunction.DEFAULT_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ));
        TPLogger.attachUnhandledExceptionLogger();


        // new Client(new InetSocketAddress("35.234.123.221", 30002));

        final Identity identityA = new Identity("Node.A");
        final Identity identityB = new Identity("Node.B");

        final NKNClient clientA = new NKNClient(identityA)
                .onSimpleMessage((from, message) -> System.out.println("ClientA: New message from " + from + "\n  ==> " + message))
                .start();
        final NKNClient clientB = new NKNClient(identityB)
                .onSimpleMessage((from, message) -> System.out.println("ClientB: New message from " + from + "\n  ==> " + message))
                .start();

        clientA.sendSimpleMessage(identityB.getFullIdentifier(), "Hello!");

    }

}
