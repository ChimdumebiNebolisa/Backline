package dev.backline.cli.commands;

import dev.backline.cli.io.ResourceCopier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Creates {@code examples/sample-api/backline.yml} and README from embedded templates.
 */
@Command(mixinStandardHelpOptions = true, name = "init", description = "Create sample files under examples/sample-api/.")
public class SampleInitCommand implements Callable<Integer> {

    @Option(names = {"--force"}, description = "Overwrite existing sample files")
    private boolean force;

    @Override
    public Integer call() throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path yml = root.resolve(Path.of("examples", "sample-api", "backline.yml"));
        Path readme = root.resolve(Path.of("examples", "sample-api", "README.md"));
        if ((Files.exists(yml) || Files.exists(readme)) && !force) {
            System.err.println("Sample files already exist. Pass --force to overwrite.");
            return 1;
        }
        ResourceCopier.copyClasspathResource("/templates/backline.sample.yml", yml);
        ResourceCopier.copyClasspathResource("/templates/sample.README.md", readme);
        System.out.println(yml.toAbsolutePath().normalize());
        System.out.println(readme.toAbsolutePath().normalize());
        return 0;
    }
}
