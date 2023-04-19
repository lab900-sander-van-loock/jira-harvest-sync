package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import be.sandervl.jiraharvest.services.JiraIssueParser;
import be.sandervl.jiraharvest.services.JiraService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ShellComponent
public class DryRunMerge {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final JiraService jiraService;
    private final HarvestService harvestService;
    private final JiraIssueParser issueParser;

    public DryRunMerge(JiraService jiraService, HarvestService harvestService, JiraIssueParser issueParser) {
        this.jiraService = jiraService;
        this.harvestService = harvestService;
        this.issueParser = issueParser;
    }

    @ShellMethod(value = "Dry Run a Jira Harvest sync", key = "dry-run")
    public String dryRun() {
        var existingTimeEntryNotes = StreamSupport.stream(harvestService.getTimeEntries().spliterator(), false)
                .map(HarvestService.TimeEntry::notes)
                .filter(note -> note != null && !note.equals(""))
                .collect(Collectors.toSet());
        var projectAssignments = StreamSupport.stream(harvestService.getProjectAssignments().spliterator(), false).collect(Collectors.toSet());
        var jiraIssues = StreamSupport.stream(jiraService.getIssues().spliterator(), false);
        return jiraIssues
                .filter(issue -> !existingTimeEntryNotes.contains(issue.key()))
                .map(issue -> convert(projectAssignments, issue))
                .collect(Collectors.joining("\n---------------------------------------------------\n"));
    }

    private String convert(Set<HarvestService.ProjectAssignment> projectAssignments, JiraService.BasicIssue issue) {

        try {
            var projectAssignmentsForClient = projectAssignments.stream()
                    .filter(assignment -> issue.fields().summary().contains(assignment.client().name()))
                    .collect(Collectors.toSet());
            if (projectAssignmentsForClient.size() < 1) {
                throw new RuntimeException("No project found for issue " + issue.fields().summary());
            }
            var issueIsBillable = issue.fields().labels().contains("HARVEST-Billable");

            var entry = projectAssignmentsForClient.stream()
                    .filter(assignment -> assignment.taskAssignments().stream().anyMatch(isBillable(issueIsBillable)))
                    .flatMap(ass -> ass.taskAssignments().stream().collect(Collectors.toMap(e -> e, e -> ass, (a, b) -> a, () -> new TreeMap<>(matchingTaskComparator()))).entrySet().stream())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No Project/Task found for issue " + issue.fields().summary()));

            var projectAssignment = entry.getValue();
            var projectId = projectAssignment.project().id();
            var taskAssignment = entry.getKey();
            Long taskId = taskAssignment.task().id();
            LocalDate spentDate = issueParser.getWorkedOnTimeForIssue(issue).orElseThrow(() -> new RuntimeException("No date found for issue " + issue.key()));
            Double hours = Math.max(1, issueParser.getWorkedTimeForIssue(issue).map(Duration::toHours).map(Double::valueOf).orElseThrow(() -> new RuntimeException("No time found for issue " + issue.key())));
            String notes = issue.key();
            return String.format("""
                    This Jira issue '%s' will be converted to a Harvest time entry.
                    Client: %s
                    Project: %s
                    Task: %s
                    Spent Date: %s
                    Hours: %s
                    Notes: %s
                    """, issue.fields().summary(), projectAssignment.client().name(), projectAssignment.project().name(), taskAssignment.task().name(), spentDate, hours, notes);
            //harvestService.create(projectId, taskId, spentDate, hours, notes);
        } catch (RuntimeException e) {
            LOG.warning(e.getMessage());
            return String.format("""
                    Could not create entry for issue %s
                    Failure reason: %s
                                        """, issue.fields().summary(), e.getMessage());
        }
    }

    private static Comparator<HarvestService.TaskAssignment> matchingTaskComparator() {
        return (a, b) -> {
            if (isRelevantForDevs(a) && isRelevantForDevs(b)) {
                return a.task().name().compareTo(b.task().name());
            }
            if (isRelevantForDevs(a)) {
                return -1;
            }
            if (isRelevantForDevs(b)) {
                return 1;
            }
            return a.task().name().compareTo(b.task().name());
        };
    }

    private static boolean isRelevantForDevs(HarvestService.TaskAssignment taskAssignment) {
        return taskAssignment.task().name().toLowerCase().contains("billable");
    }

    private static Predicate<HarvestService.TaskAssignment> isBillable(boolean issueIsBillable) {
        return taskAssignment -> taskAssignment.billable() == issueIsBillable;
    }
}
