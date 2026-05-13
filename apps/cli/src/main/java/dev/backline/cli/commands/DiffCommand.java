package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffEntry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Prints a grouped diff for a run versus the previous completed run. */
@Command(mixinStandardHelpOptions = true, name = "diff", description = "Show regression diff for a run.")
public class DiffCommand implements Callable<Integer> {

    @ParentCommand
    private Backline parent;

    @Parameters(index = "0", description = "Run id", arity = "1")
    private UUID runId;

    @Override
    public Integer call() throws Exception {
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        var diff = client.getRunDiff(runId);
        Map<RunDiffChangeType, List<RunDiffEntry>> grouped = new EnumMap<>(RunDiffChangeType.class);
        List<RunDiffEntry> entries = diff.entries() == null ? List.of() : diff.entries();
        for (RunDiffEntry e : entries) {
            grouped.computeIfAbsent(e.changeType(), k -> new ArrayList<>()).add(e);
        }
        System.out.println("diff for run " + diff.runId() + " vs " + diff.previousRunId());
        for (var type : RunDiffChangeType.values()) {
            List<RunDiffEntry> list = grouped.get(type);
            if (list == null || list.isEmpty()) {
                continue;
            }
            System.out.println();
            System.out.println("[" + type + "]");
            for (RunDiffEntry e : list) {
                System.out.println(
                        "- " + e.checkKey() + " (" + e.checkName() + ") " + e.previousStatus() + " -> " + e.currentStatus());
            }
        }
        return 0;
    }
}
