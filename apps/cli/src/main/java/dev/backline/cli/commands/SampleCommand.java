package dev.backline.cli.commands;

import picocli.CommandLine.Command;

@Command(mixinStandardHelpOptions = true, name = "sample", description = "Sample API helpers.", subcommands = {SampleInitCommand.class, SampleServeCommand.class})
public class SampleCommand {}
