package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.JiraIssueParser;
import be.sandervl.jiraharvest.services.JiraService;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class ListJiraIssues {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final JiraService jiraService;
    private final JiraIssueParser issueParser;

    public ListJiraIssues(JiraService jiraService, JiraIssueParser issueParser) {
        this.jiraService = jiraService;
        this.issueParser = issueParser;
    }

    @ShellMethod(value = "List Jira issues", key = "lsj")
    public String listJiraIssues() {
    return StreamSupport.stream(jiraService.getIssues().spliterator(), false)
        .map(this::formatIssue)
        .collect(Collectors.joining("\n"));
    }

    private String formatIssue(JiraService.BasicIssue issue) {
        return
                String.format("""
                        %s: %s
                        %s
                        Estimated time: %s
                        """, issue.key(), issue.fields().summary(), String.join(",", issue.fields().labels()), issueParser.getWorkedTimeForIssue(issue).map(d -> d.toHours() + "h").orElse("N/A"));
    }
}
