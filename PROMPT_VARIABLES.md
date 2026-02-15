# Prompt Template Variables

When writing a custom prompt in AI Commits, you can use the following placeholder variables.
They are replaced with real values at commit-message generation time
(see `AICommitsUtils.constructPrompt()`).

## Available Variables

| Variable | Resolves To | Notes |
|---|---|---|
| `{locale}` | The project's configured locale name in English (e.g. "English", "German", "French"). | Useful for telling the LLM which language to reply in. |
| `{diff}` | The computed git diff of staged changes. | If you omit this variable from your prompt, the diff is automatically appended to the end. |
| `{branch}` | The current VCS branch name (Git or SVN). | Falls back to `main` (with a notification) if the branch cannot be determined. |
| `{hint}` | A free-text hint provided by the user in the commit dialog. | See **Hint Syntax** below for simple and conditional forms. |
| `{taskId}` | The ID of the active IntelliJ task (e.g. a JIRA issue key). | Only replaced when IntelliJ's Task Manager is available. |
| `{taskSummary}` | The summary / title of the active IntelliJ task. | Only replaced when IntelliJ's Task Manager is available. |
| `{taskDescription}` | The full description of the active IntelliJ task. | Resolves to an empty string if the task has no description. Only replaced when IntelliJ's Task Manager is available. |
| `{taskTimeSpent}` | Total time spent on the active task, formatted as `HH:mm`. | Only replaced when IntelliJ's Task Manager is available. |

## Hint Syntax

The `{hint}` variable supports two forms:

### Simple form

```
Write a commit message. Use this hint: {hint}.
```

`{hint}` is replaced with the hint text, or an empty string if no hint was provided.

### Conditional form (recommended)

```
{Use this hint to improve the commit message: $hint}
```

Wrap a section containing `$hint` (with a dollar-sign prefix) inside curly braces.

- **When a hint is provided:** the braces are removed and `$hint` is replaced with the hint text.
  Result: `Use this hint to improve the commit message: fix the typo`
- **When no hint is provided:** the entire block (including braces) is removed, keeping the prompt clean.

## Examples

### Minimal prompt

```
Write a concise commit message in {locale} for the following diff:
{diff}
```

### Conventional commits with branch and hint

```
Write a commit message following the conventional commit convention.
Use {locale} language. The branch is: {branch}.
{Use this additional context: $hint}
{diff}
```

### With task manager integration

```
Write a commit message in {locale}.
Task: {taskId} - {taskSummary}
{diff}
```
