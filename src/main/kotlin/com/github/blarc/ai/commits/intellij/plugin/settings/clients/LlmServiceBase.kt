package com.github.blarc.ai.commits.intellij.plugin.settings.clients

import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.constructPrompt
import com.github.blarc.ai.commits.intellij.plugin.AICommitsVcsUtils.computeDiff
import com.github.blarc.ai.commits.intellij.plugin.AICommitsVcsUtils.getCommonBranch
import com.github.blarc.ai.commits.intellij.plugin.AICommitsVcsUtils.getLastCommitChanges
import com.github.blarc.ai.commits.intellij.plugin.AICommitsVcsUtils.getPreviousCommitMessages
import com.github.blarc.ai.commits.intellij.plugin.notifications.Notification
import com.github.blarc.ai.commits.intellij.plugin.notifications.sendNotification
import com.github.blarc.ai.commits.intellij.plugin.settings.ProjectSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.isAmendCommitMode
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * Base class for all LLM service implementations, providing shared functionality
 * for commit message generation regardless of execution model (API-based or CLI-based).
 */
abstract class LlmServiceBase<C : LlmClientConfiguration>(protected val coroutineScope: CoroutineScope) {

    var generateCommitMessageJob: Job? = null

    /**
     * Prepares the commit message request by computing diff and constructing prompt.
     * Returns (diff, prompt) pair or null if diff is empty.
     */
    protected suspend fun prepareCommitMessageRequest(
        commitWorkflowHandler: AbstractCommitWorkflowHandler<*, *>,
        project: Project
    ): Pair<String, String>? {
        val commitContext = commitWorkflowHandler.workflow.commitContext
        val includedChanges = commitWorkflowHandler.ui.getIncludedChanges().toMutableList()

        if (commitContext.isAmendCommitMode) {
            includedChanges += getLastCommitChanges(project)
        }

        val diff = computeDiff(includedChanges, false, project)
        if (diff.isBlank()) {
            withContext(Dispatchers.EDT) {
                sendNotification(Notification.emptyDiff())
            }
            return null
        }

        val branch = getCommonBranch(includedChanges, project)

        val activePrompt = project.service<ProjectSettings>().activePrompt

        val previousCommits = getPreviousCommitMessages(
            activePrompt.numberOfPreviousCommits,
            includedChanges,
            project
        )

        val prompt = constructPrompt(
            activePrompt.content,
            diff,
            branch,
            commitWorkflowHandler.getCommitMessage(),
            previousCommits,
            project
        )

        return Pair(diff, prompt)
    }

    abstract fun generateCommitMessage(
        clientConfiguration: C,
        commitWorkflowHandler: AbstractCommitWorkflowHandler<*, *>,
        project: Project
    )
}
