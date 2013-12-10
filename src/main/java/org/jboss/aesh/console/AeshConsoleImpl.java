/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.aesh.console;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.exception.CommandLineParserException;
import org.jboss.aesh.cl.parser.CommandLineCompletionParser;
import org.jboss.aesh.cl.parser.ParsedCompleteObject;
import org.jboss.aesh.cl.validator.CommandValidatorException;
import org.jboss.aesh.cl.validator.OptionValidatorException;
import org.jboss.aesh.complete.CompleteOperation;
import org.jboss.aesh.complete.Completion;
import org.jboss.aesh.console.command.CommandNotFoundException;
import org.jboss.aesh.console.command.CommandResult;
import org.jboss.aesh.console.command.ConsoleCommand;
import org.jboss.aesh.console.command.completer.CompleterInvocationProvider;
import org.jboss.aesh.console.command.container.CommandContainer;
import org.jboss.aesh.console.command.converter.ConverterInvocationProvider;
import org.jboss.aesh.console.command.invocation.AeshCommandInvocation;
import org.jboss.aesh.console.command.invocation.CommandInvocationProvider;
import org.jboss.aesh.console.command.invocation.CommandInvocationServices;
import org.jboss.aesh.console.command.registry.AeshInternalCommandRegistry;
import org.jboss.aesh.console.command.registry.CommandRegistry;
import org.jboss.aesh.console.command.validator.ValidatorInvocationProvider;
import org.jboss.aesh.console.helper.ManProvider;
import org.jboss.aesh.console.man.Man;
import org.jboss.aesh.console.operator.ControlOperator;
import org.jboss.aesh.console.settings.CommandNotFoundHandler;
import org.jboss.aesh.console.settings.Settings;
import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.terminal.Shell;
import org.jboss.aesh.util.LoggerUtil;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshConsoleImpl implements AeshConsole {

    private final Console console;
    private final CommandRegistry registry;
    private final CommandInvocationServices commandInvocationServices;
    private final InvocationProviders invocationProviders;

    private final Logger logger = LoggerUtil.getLogger(AeshConsoleImpl.class
            .getName());
    private final ManProvider manProvider;
    private final CommandNotFoundHandler commandNotFoundHandler;
    private final AeshContext aeshContext;
    private AeshInternalCommandRegistry internalRegistry;
    private String commandInvocationProvider = CommandInvocationServices.DEFAULT_PROVIDER_NAME;

    AeshConsoleImpl(Settings settings, CommandRegistry registry,
                    CommandInvocationServices commandInvocationServices,
                    CommandNotFoundHandler commandNotFoundHandler,
                    CompleterInvocationProvider completerInvocationProvider,
                    ConverterInvocationProvider converterInvocationProvider,
                    ValidatorInvocationProvider validatorInvocationProvider,
                    ManProvider manProvider, File cwd) {
        this.registry = registry;
        this.commandInvocationServices = commandInvocationServices;
        this.commandNotFoundHandler = commandNotFoundHandler;
        this.manProvider = manProvider;
        this.invocationProviders =
                new AeshInvocationProviders(converterInvocationProvider, completerInvocationProvider,
                        validatorInvocationProvider);

        aeshContext = new AeshContextImpl(cwd);
        console = new Console(settings);
        console.setConsoleCallback(new AeshConsoleCallback(this));
        console.addCompletion(new AeshCompletion());
        processSettings(settings);
    }

    @Override
    public void start() {
        console.start();
    }

    @Override
    public void stop() {
        try {
            console.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return registry;
    }

    @Override
    public void setPrompt(Prompt prompt) {
        console.setPrompt(prompt);
    }

    @Override
    public Prompt getPrompt() {
        return console.getPrompt();
    }

    @Override
    public void attachConsoleCommand(ConsoleCommand consoleCommand) {
        console.attachProcess(consoleCommand);
    }

    @Override
    public Shell getShell() {
        return console.getShell();
    }

    @Override
    public void clear() {
        try {
            console.clear();
        } catch (IOException ignored) {
        }
    }

    @Override
    public String getHelpInfo(String commandName) {
        try (CommandContainer commandContainer = registry.getCommand(commandName, "")){
            if(commandContainer != null)
                return commandContainer.getParser().printHelp();
        }
        catch (CommandNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void setCurrentCommandInvocationProvider(String name) {
        this.commandInvocationProvider = name;
    }

    @Override
    public void registerCommandInvocationProvider(String name,
                                                  CommandInvocationProvider commandInvocationProvider) {
        commandInvocationServices.registerProvider(name,
                commandInvocationProvider);
    }

    @Override
    public ManProvider getManProvider() {
        return manProvider;
    }

    @Override
    public void executeCommand(String command) {
        console.getConsoleCallback().readConsoleOutput(
                new ConsoleOperation(ControlOperator.NONE, command));
    }

    @Override
    public AeshContext getContext() {
        return aeshContext;
    }

    public String getBuffer() {
        return console.getBuffer();
    }

    private void processSettings(Settings settings) {
        if (settings.isManEnabled()) {
            internalRegistry = new AeshInternalCommandRegistry();
            internalRegistry.addCommand(new Man(manProvider));
        }
    }

    private List<String> completeCommandName(String input) {
        List<String> matchedCommands = new ArrayList<String>();
        try {
            for (String commandName : registry.getAllCommandNames()) {
                if (commandName.startsWith(input))
                    matchedCommands.add(commandName);
            }
            if (internalRegistry != null) {
                for (String commandName : internalRegistry.getAllCommandNames())
                    if (commandName.startsWith(input))
                        matchedCommands.add(commandName);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Error retrieving command names from CommandRegistry", e);
        }

        return matchedCommands;
    }

    /**
     * try to return the command in the given registry if the given registry do
     * not find the command, check if we have a internal registry and if its
     * there.
     *
     * @param name
     *            command name
     * @param line
     *            command line
     * @return command
     * @throws CommandNotFoundException
     */
    private CommandContainer getCommand(String name, String line)
            throws CommandNotFoundException {
        try {
            return registry.getCommand(name, line);
        } catch (CommandNotFoundException e) {
            if (internalRegistry != null) {
                CommandContainer cc = internalRegistry.getCommand(name);
                if (cc != null)
                    return cc;
            }
            throw e;
        }
    }

    private void detachProcess() {
        console.detachProcess();
    }

    class AeshCompletion implements Completion {

        @Override
        public void complete(CompleteOperation completeOperation) {
            List<String> completedCommands = completeCommandName(completeOperation
                    .getBuffer());
            if (completedCommands.size() > 0) {
                completeOperation.addCompletionCandidates(completedCommands);
            } else {
                try (CommandContainer commandContainer = getCommand(
                        Parser.findFirstWord(completeOperation.getBuffer()),
                        completeOperation.getBuffer())) {
                    CommandLineCompletionParser completionParser = commandContainer
                            .getParser().getCompletionParser();

                    ParsedCompleteObject completeObject = completionParser
                            .findCompleteObject(completeOperation.getBuffer(),
                                    completeOperation.getCursor());
                    completionParser.injectValuesAndComplete(completeObject,
                            commandContainer.getCommand(), completeOperation,
                            invocationProviders);
                } catch (CommandLineParserException e) {
                    logger.warning(e.getMessage());
                } catch (CommandNotFoundException ignored) {
                    if (commandNotFoundHandler != null)
                        commandNotFoundHandler.handleCommandNotFound(
                                completeOperation.getBuffer(), getShell());
                } catch (Exception ex) {
                    logger.log(Level.SEVERE,
                            "Runtime exception when completing: "
                                    + completeOperation, ex);
                }
            }
        }

    }

    class AeshConsoleCallback implements ConsoleCallback {

        private final AeshConsole console;

        AeshConsoleCallback(AeshConsole aeshConsole) {
            this.console = aeshConsole;
        }

        @Override
        public int readConsoleOutput(ConsoleOperation output) {
            CommandResult result = CommandResult.SUCCESS;
            if (output != null && output.getBuffer().trim().length() > 0) {
                try (CommandContainer commandContainer = getCommand(
                        Parser.findFirstWord(output.getBuffer()),
                        output.getBuffer())) {

                    CommandLine commandLine = commandContainer.getParser()
                            .parse(output.getBuffer());

                    commandContainer
                            .getParser()
                            .getCommandPopulator()
                            .populateObject(commandContainer.getCommand(),
                                    commandLine, invocationProviders, true);
                    // validate the command before execute, only call if no
                    // options with overrideRequired is not set
                    if (commandContainer.getParser().getCommand()
                            .getValidator() != null
                            && !commandLine.hasOptionWithOverrideRequired())
                        commandContainer.getParser().getCommand()
                                .getValidator()
                                .validate(commandContainer.getCommand());
                    result = commandContainer
                            .getCommand()
                            .execute(
                                    commandInvocationServices
                                            .getCommandInvocationProvider(
                                                    commandInvocationProvider)
                                            .enhanceCommandInvocation(
                                                    new AeshCommandInvocation(
                                                            console,
                                                            output.getControlOperator())));
                } catch (CommandLineParserException e) {
                    getShell().out().println(e.getMessage());
                    result = CommandResult.FAILURE;
                } catch (CommandNotFoundException e) {
                    if (commandNotFoundHandler != null) {
                        commandNotFoundHandler.handleCommandNotFound(
                                output.getBuffer(), getShell());
                    } else {
                        getShell().out().print(
                                "Command not found: "
                                        + Parser.findFirstWord(output
                                        .getBuffer())
                                        + Config.getLineSeparator());
                    }
                    result = CommandResult.FAILURE;
                } catch (OptionValidatorException e) {
                    getShell().out().println(e.getMessage());
                    result = CommandResult.FAILURE;
                } catch (CommandValidatorException e) {
                    getShell().out().println(e.getMessage());
                    result = CommandResult.FAILURE;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception when parsing/running: "
                            + output.getBuffer(), e);
                    getShell().out().println(
                            "Exception when parsing/running: "
                                    + output.getBuffer() + ", "
                                    + e.getMessage());
                    detachProcess();
                    result = CommandResult.FAILURE;
                }
            }
            // empty line
            else if (output != null) {
                result = CommandResult.FAILURE;
            } else {
                stop();
                result = CommandResult.FAILURE;
            }

            if (result == CommandResult.SUCCESS)
                return 0;
            else
                return 1;
        }
    }
}
