package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.JiraService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ShellComponent
public class ListJiraIssues {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final JiraService jiraService;

    public ListJiraIssues(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    @ShellMethod(value = "List Jira issues", key = "lsj")
    public String listJiraIssues() {
        return StreamSupport.stream(jiraService.getIssues().spliterator(), false)
                .map(issue -> formatIssue(issue))
                .collect(Collectors.joining("\n"));
    }

    private String formatIssue(JiraService.BasicIssue issue) {
        return
                String.format("""
                        %s: %s
                        %s
                        Estimated time: %s
                        """, issue.key(), issue.fields().summary(), String.join(",", issue.fields().labels()), jiraService.getWorkedTimeForIssue(issue).map(d -> d.toHours() + "h").orElse("N/A"));
    }
}
