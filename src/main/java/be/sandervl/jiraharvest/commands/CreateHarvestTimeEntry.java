package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import be.sandervl.jiraharvest.services.JiraService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.logging.Logger;
import java.util.stream.StreamSupport;

@ShellComponent
public class CreateHarvestTimeEntry {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final HarvestService harvestService;
    private final JiraService jiraService;

    public CreateHarvestTimeEntry(HarvestService harvestService, JiraService jiraService) {
        this.harvestService = harvestService;
        this.jiraService = jiraService;
    }

    @ShellMethod(value = "List Jira issues", key = "create")
    public void createHarvestEntry() {
        var harvestProjects = harvestService.getProjectAssignments();
        jiraService.getIssues().forEach(issue -> processIssue(issue, harvestProjects));
    }

    private void processIssue(JiraService.BasicIssue issue, Iterable<HarvestService.ProjectAssignment> projectAssignments) {
        LOG.info(issue.key() + " " + issue.fields().summary() + " " + issue.fields().labels());
        try {
            HarvestService.ProjectAssignment projectAssignment = getProjectAssignment(issue, projectAssignments);
            var projectId = projectAssignment.project().id();
            LOG.info("Project ID: " + projectId);
            var taskId = getTask(projectAssignment, issue).task().id();
            LOG.info("Task ID: " + taskId);
            var notes = issue.key();
            //var created = harvestService.create(projectId, taskId,LocalDate.now(), 1.5, "TEST");
            //LOG.info("Created time entry in Harvest: " + test);
        } catch (JiraParseException e) {
            LOG.warning("Unable to process " + issue.key() + " because of " + e.getMessage());
        }
    }

    private HarvestService.TaskAssignment getTask(HarvestService.ProjectAssignment projectAssignment, JiraService.BasicIssue issue) throws JiraParseException {
        var labels = issue.fields().labels();
        var nonBillable = labels.stream().anyMatch(l -> l.toLowerCase().contains("non-billable"));
        return projectAssignment.taskAssignments()
                .stream()
                .filter(t -> {
                    if (nonBillable) {
                        return t.task().name().toLowerCase().contains("non-billable");
                    } else {
                        return t.task().name().toLowerCase().matches(".*(dev|tech|support).*");
                    }
                })
                .findAny()
                .orElseThrow(() -> new JiraParseException("No matching task could be found for project " + projectAssignment.project().name() + " and issue " + issue.key()));
    }

    private HarvestService.ProjectAssignment getProjectAssignment(JiraService.BasicIssue issue, Iterable<HarvestService.ProjectAssignment> projectAssignments) throws JiraParseException {
        var summary = issue.fields().summary();
        String[] split = summary.split(" - ");
        if (summary.split(" - ").length == 0) {
            throw new JiraParseException("Given Summary not valid: " + summary);
        }
        var jireClientName = split[0].trim();


        return StreamSupport.stream(projectAssignments.spliterator(), false)
                .filter(hpn -> hpn.client().name().contains(jireClientName))
                .findFirst()
                .orElseThrow(() -> new JiraParseException("No valid Project found for summary " + summary));
    }

    private static class JiraParseException extends Throwable {
        public JiraParseException(String message) {
            super(message);
        }
    }
}
