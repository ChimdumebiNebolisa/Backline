package dev.backline.cli.commands;

import dev.backline.cli.Backline;
import dev.backline.cli.client.ApiClientException;
import dev.backline.cli.client.BacklineApiClient;
import dev.backline.core.api.dto.DiffBaselineStrategy;
import dev.backline.core.api.dto.RunDiffDto;
import dev.backline.core.api.dto.RunDiffChangeType;
import dev.backline.core.api.dto.RunDiffEntry;
import dev.backline.core.contract.ContractChangeDetail;
import dev.backline.core.contract.ContractChangeKind;
import dev.backline.core.contract.ContractPathChange;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
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

    @Option(
            names = {"--baseline"},
            description = "Diff baseline strategy: ${COMPLETION-CANDIDATES}",
            defaultValue = "PREVIOUS_COMPLETED")
    private DiffBaselineStrategy baseline;

    @Option(
            names = {"--fixed-run-id", "--baseline-run-id"},
            description = "Required when --baseline=FIXED_RUN")
    private UUID fixedRunId;

    @Override
    public Integer call() throws Exception {
        BacklineApiClient client = new BacklineApiClient(parent.apiUrl());
        if (baseline == DiffBaselineStrategy.FIXED_RUN && fixedRunId == null) {
            System.err.println("--fixed-run-id is required when --baseline=FIXED_RUN");
            return 2;
        }
        RunDiffDto diff;
        try {
            diff = client.getRunDiff(runId, baseline, fixedRunId);
        } catch (ApiClientException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        } catch (InterruptedException e) {
            return CliApiErrors.printInterrupted();
        } catch (IOException e) {
            return CliApiErrors.print(parent.apiUrl(), e);
        }
        Map<RunDiffChangeType, List<RunDiffEntry>> grouped = new EnumMap<>(RunDiffChangeType.class);
        List<RunDiffEntry> entries = diff.entries() == null ? List.of() : diff.entries();
        for (RunDiffEntry e : entries) {
            grouped.computeIfAbsent(e.changeType(), k -> new ArrayList<>()).add(e);
        }
        if (diff.previousRunId() == null) {
            System.out.println("diff for run " + diff.runId() + ": no baseline run found (baseline="
                    + baseline + ")");
        } else {
            System.out.println("diff for run " + diff.runId() + " vs " + diff.previousRunId()
                    + " (baseline=" + baseline + ")");
        }
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
                printContractChange(e.contractChange());
            }
        }
        return 0;
    }

    private static void printContractChange(ContractChangeDetail detail) {
        if (detail == null || detail.classification() == null) {
            return;
        }
        switch (detail.classification()) {
            case BREAKING -> System.out.println("  BREAKING CONTRACT DRIFT");
            case ADDITIVE -> System.out.println("  ADDITIVE CONTRACT DRIFT");
            case NOISY -> System.out.println("  NOISY CONTRACT DRIFT");
            case UNAVAILABLE -> System.out.println("  CONTRACT CAPTURE UNAVAILABLE");
            case UNCHANGED -> {
                if (detail.truncated()) {
                    System.out.println("  CONTRACT UNCHANGED (truncated)");
                }
                return;
            }
        }
        if (detail.truncated()) {
            System.out.println("  (contract capture truncated on one or both sides)");
        }
        List<ContractPathChange> changes = detail.changes() == null ? List.of() : detail.changes();
        for (ContractPathChange change : changes) {
            System.out.println("  " + formatPathChange(change));
        }
    }

    private static String formatPathChange(ContractPathChange change) {
        if (change.kind() == ContractChangeKind.REMOVED) {
            return "- removed " + change.path() + " (was " + joinTypes(change.previousTypes()) + ")";
        }
        if (change.kind() == ContractChangeKind.ADDED) {
            return "+ added " + change.path() + " (" + joinTypes(change.currentTypes()) + ")";
        }
        return "- changed " + change.path() + " from " + joinTypes(change.previousTypes())
                + " to " + joinTypes(change.currentTypes());
    }

    private static String joinTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return "—";
        }
        return String.join("|", types);
    }
}
