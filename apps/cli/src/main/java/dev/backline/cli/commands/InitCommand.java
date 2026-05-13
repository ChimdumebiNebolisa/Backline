package dev.backline.cli.commands;

import dev.backline.cli.io.ResourceCopier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Creates {@code backline.yml} in the working directory from the embedded sample template.
 */
@Command(mixinStandardHelpOptions = true, name = "init", description = "Create backline.yml in the current directory from the embedded template.")
public class InitCommand implements Callable<Integer> {

    @Option(names = {"--force"}, description = "Overwrite an existing backline.yml")
    private boolean force;

    @Override
    public Integer call() throws Exception {
        Path target = Path.of("backline.yml").toAbsolutePath().normalize();
        if (Files.exists(target) && !force) {
            System.err.println("backline.yml already exists. Pass --force to overwrite.");
            return 1;
        }
        ResourceCopier.copyClasspathResource("/templates/backline.sample.yml", target);
        System.out.println(target);
        return 0;
    }
}
