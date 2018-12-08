package com.github.mvh77.log4j2layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.pattern.NameAbbreviator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.time.Instant;

/**
 * Log4J @{@link Layout} generating JSON encoded log events using Google Gson.
 */
@Plugin(name = "GsonJsonLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class GsonJsonLayout extends AbstractStringLayout {

    private static final int DEFAULT_MAX_STACK_SIZE = 20;
    private final String stacktraceKey = "stack-trace";
    private final String loggerKey = "logger";
    private final Gson gson;
    private final int maxStackSize;
    private final NameAbbreviator loggerPatternConverter;
    private final boolean stackTraceAsJson;

    @PluginFactory
    public static GsonJsonLayout create(@PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset,
                                        @PluginAttribute(value = "pretty", defaultBoolean = false) boolean pretty,
                                        @PluginAttribute(value = "json-stacktrace", defaultBoolean = false) boolean jsonStacktrace,
                                        @PluginAttribute(value = "max-stack-size", defaultInt = DEFAULT_MAX_STACK_SIZE) int maxStackSize,
                                        @PluginAttribute(value = "logger-pattern") String defaultLoggerPattern) {
        return new GsonJsonLayout(charset, pretty, jsonStacktrace, maxStackSize, defaultLoggerPattern);
    }

    protected GsonJsonLayout(Charset charset, boolean pretty, boolean stackTraceAsJson, int maxStackSize, String loggerPattern) {
        super(charset);
        this.gson = pretty ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        this.maxStackSize = maxStackSize > 0 ? maxStackSize : DEFAULT_MAX_STACK_SIZE;
        this.loggerPatternConverter = (loggerPattern == null || loggerPattern.isEmpty()) ? null : NameAbbreviator.getAbbreviator(loggerPattern);
        this.stackTraceAsJson = stackTraceAsJson;
    }

    @Override
    public String toSerializable(LogEvent e) {
        JsonObject root = new JsonObject();

        root.addProperty("@timestamp", Instant.ofEpochMilli(e.getTimeMillis()).toString());
        root.addProperty("thread", e.getThreadName());
        root.addProperty("level", e.getLevel().toString());
        if(loggerPatternConverter == null) {
            root.addProperty(loggerKey, e.getLoggerName());
        } else {
            StringBuilder b = new StringBuilder();
            loggerPatternConverter.abbreviate(e.getLoggerName(), b);
            root.addProperty(loggerKey, b.toString());
        }
        root.addProperty("message", e.getMessage().getFormattedMessage());

        ThrowableProxy proxy = e.getThrownProxy();
        if(proxy != null) {
            if(stackTraceAsJson)
                root.add(stacktraceKey, stackTraceAsJson(proxy));
            else
                root.addProperty(stacktraceKey, stackTraceAsString(proxy));
        }
        return gson.toJson(root) + "\n";
    }

    private String stackTraceAsString(ThrowableProxy proxy) {
        StringWriter sw = new StringWriter();
        proxy.getThrowable().printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private JsonArray stackTraceAsJson(ThrowableProxy proxy) {
        JsonArray stack = new JsonArray();
        int count = 0; // prevent infinite/enormous stacks
        while(proxy != null && count < maxStackSize) {
            JsonObject element = new JsonObject();
            String head;
            if(count > 0) {
                head = "caused by";
            } else {
                head = "exception";
            }
            element.addProperty(head, proxy.getThrowable().getClass().getName());
            if(proxy.getMessage() != null) {
                element.addProperty("message", proxy.getMessage());
            }

            JsonArray frames = new JsonArray();
            for(StackTraceElement frame : proxy.getStackTrace()) {
                frames.add("at " + frame.toString());
            }
            if(frames.size() != 0) {
                element.add("frames", frames);
            }
            stack.add(element);
            proxy = proxy.getCauseProxy();
            count++;
        }
        return stack;
    }
}
