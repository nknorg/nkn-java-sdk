package jsmith.nknsdk.examples;

import com.darkyen.tproll.LogFunction;
import com.darkyen.tproll.TPLogger;
import com.darkyen.tproll.TPLoggerFactory;
import com.darkyen.tproll.logfunctions.FileLogFunction;
import com.darkyen.tproll.logfunctions.LogFunctionMultiplexer;
import com.darkyen.tproll.logfunctions.SimpleLogFunction;
import org.slf4j.Marker;

import java.io.File;

/**
 *
 */
public class LogUtils {


    public static void setupLogging(byte level) {
        switch (level) {
            case TPLogger.TRACE: TPLogger.TRACE(); break;
            case TPLogger.DEBUG: TPLogger.DEBUG(); break;
            case TPLogger.INFO: TPLogger.INFO(); break;
            case TPLogger.WARN: TPLogger.WARN(); break;
            case TPLogger.ERROR: TPLogger.ERROR(); break;
            default: TPLogger.INFO();
        }

        TPLogger.setLogFunction(
                new LogFilter(new LogFunctionMultiplexer(
                        SimpleLogFunction.CONSOLE_LOG_FUNCTION, // Log to console
                        new FileLogFunction(new File("logs")) // & Log to file in "logs" directory
                ))
        );

        TPLoggerFactory.USE_SHORT_NAMES = false;

        TPLogger.attachUnhandledExceptionLogger();
    }



    private static class LogFilter extends LogFunction {

        private final LogFunction parent;

        public LogFilter(LogFunction parent) {
            this.parent = parent;
        }

        @Override
        public void log(String name, long time, byte level, Marker marker, CharSequence content) {
            String shortname = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
            shortname += " @" + Thread.currentThread().getName();
            if (level <= TPLogger.DEBUG) {
                if (name.startsWith("jsmith.nknsdk.")) { // Filter by package
                    parent.log(shortname, time, level, marker, content);
                }
            } else {
                parent.log(shortname, time, level, marker, content);
            }
        }

        @Override
        public boolean isEnabled(byte level, Marker marker) {
            return parent.isEnabled(level, marker);
        }
    }
}
