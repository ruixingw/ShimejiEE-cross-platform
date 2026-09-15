package com.group_finity.mascot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Single line log format (like the original shimeji).
 */
public class LogFormatter extends SimpleFormatter {

    private final Date dat = new Date();
    private static final String FORMAT = "{0,date} {0,time}";
    private MessageFormat formatter;

    private final Object[] args = new Object[1];

    private final String lineSeparator = System.getProperty("line.separator");

    @Override
    public synchronized String format(final LogRecord record) {
        final var sb = new StringBuilder();

        this.dat.setTime(record.getMillis());
        this.args[0] = this.dat;
        if (this.formatter == null) {
            this.formatter = new MessageFormat(FORMAT);
        }
        final var text = new StringBuffer();
        this.formatter.format(this.args, text, null);
        sb.append(text);
        sb.append(" ");

        sb.append(record.getLevel().getLocalizedName());
        sb.append(": ");

        if (record.getSourceClassName() != null) {
            sb.append(record.getSourceClassName());
        } else {
            sb.append(record.getLoggerName());
        }
        if (record.getSourceMethodName() != null) {
            sb.append(" ");
            sb.append(record.getSourceMethodName());
        }
        sb.append(" ");

        sb.append(formatMessage(record));
        sb.append(this.lineSeparator);
        if (record.getThrown() != null) {
            try {
                final StringWriter sw = new StringWriter();
                final PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw);
            } catch (final Exception ignored) {
            }
        }
        return sb.toString();
    }
}
