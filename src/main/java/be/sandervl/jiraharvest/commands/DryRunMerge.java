package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import be.sandervl.jiraharvest.services.JiraService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.logging.Logger;

@ShellComponent
public class DryRunMerge {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final JiraService jiraService;
    private final HarvestService harvestService;

    public DryRunMerge(JiraService jiraService, HarvestService harvestService) {
        this.jiraService = jiraService;
        this.harvestService = harvestService;
    }

    @ShellMethod(value = "Dry Run a Jira Harvest sync", key = "dry-run")
    public String dryRun() {
        return null;
    }

}
