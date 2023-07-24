package org.aesh.command.impl.internal;

import org.aesh.command.invocation.InvocationProviders;
import org.aesh.readline.AeshContext;

public class convert {
    private final InvocationProviders invocationProviders;
    private final Object command;
    private final AeshContext aeshContext;
    private final boolean doValidation;

    public convert(InvocationProviders invocationProviders, Object command, AeshContext aeshContext, boolean doValidation) {
        this.invocationProviders = invocationProviders;
        this.command = command;
        this.aeshContext = aeshContext;
        this.doValidation = doValidation;
    }

    public InvocationProviders getInvocationProviders() {
        return invocationProviders;
    }

    public Object getCommand() {
        return command;
    }

    public AeshContext getAeshContext() {
        return aeshContext;
    }

    public boolean isDoValidation() {
        return doValidation;
    }
}
