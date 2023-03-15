package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.logging.Logger;

@ShellComponent
public class ListHarvestProjects {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final HarvestService harvestService;

    public ListHarvestProjects(HarvestService harvestService) {
        this.harvestService = harvestService;
    }

    @ShellMethod(value = "List Jira issues", key = "lsh")
    public void listJiraIssues() {
        harvestService.getProjectAssignments().forEach(projectAssignment -> {
            projectAssignment.taskAssignments().forEach(taskAssignment -> {
                LOG.info("Client " + projectAssignment.client().name() + " - Project " + projectAssignment.project().name() + " - Task " + taskAssignment.task().name());
            });
        });
    }
}
