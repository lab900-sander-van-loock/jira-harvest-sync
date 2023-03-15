package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.JiraService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.logging.Logger;

@ShellComponent
public class ListJiraIssues {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final JiraService jiraService;

    public ListJiraIssues(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    @ShellMethod(value = "List Jira issues", key = "lsj")
    public void listJiraIssues() {
        jiraService.getIssues().forEach(issue -> {
            LOG.info(issue.key() + " " + issue.fields().summary() + " " + issue.fields().labels());
        });
    }
}
