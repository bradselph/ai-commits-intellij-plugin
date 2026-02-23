package com.github.blarc.ai.commits.intellij.plugin

import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.isPathExcluded
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.svn.SvnUtil
import org.jetbrains.idea.svn.SvnVcs
import java.io.StringWriter

object AICommitsVcsUtils {

    suspend fun getCommonBranch(changes: List<Change>, project: Project): String? {
        return withContext(Dispatchers.IO) {
            changes.mapNotNull {
                it.virtualFile?.let { virtualFile ->
                    VcsUtil.getVcsFor(project, virtualFile)?.let { vcs ->
                        when {
                            isSvnAvailable() && vcs is SvnVcs -> {
                                SvnUtil.getUrl(vcs, VfsUtilCore.virtualToIoFile(virtualFile))?.let { url ->
                                    extractSvnBranchName(url.toDecodedString())
                                }
                            }

                            vcs is GitVcs -> {
                                GitRepositoryManager.getInstance(project)
                                    .getRepositoryForFile(it.virtualFile)
                                    ?.currentBranchName
                            }

                            else -> {
                                null
                            }
                        }
                    }
                }
            }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }
    }

    fun computeDiff(
        includedChanges: List<Change>,
        reversePatch: Boolean,
        project: Project
    ): String {
        // go through included changes, create a map of the repository to changes and discard nulls
        val changesByRepository = includedChanges
            .filter {
                it.filePath()?.path?.let { path ->
                    !isPathExcluded(path, project)
                } ?: false
            }
            .mapNotNull { change ->
                change.filePath()?.let { filePath ->
                    VcsUtil.getVcsRootFor(project, filePath)?.let { vcsRoot ->
                        vcsRoot to change
                    }
                }
            }
            .filter { !it.second.isSubmoduleChange(project) }
            .groupBy({ it.first }, { it.second })


        // compute diff for each repository
        return changesByRepository
            .map { (vcsRoot, changes) ->
                val filePatches = IdeaTextPatchBuilder.buildPatch(
                    project,
                    changes,
                    vcsRoot.toNioPath(), reversePatch, true
                )

                val stringWriter = StringWriter()
                stringWriter.write("Repository: ${vcsRoot.path}\n")
                UnifiedDiffWriter.write(project, filePatches, stringWriter, "\n", null)
                stringWriter.toString()
            }
            .joinToString("\n")
    }

    suspend fun getLastCommitChanges(project: Project): List<Change> =
        withContext(Dispatchers.IO) {
            GitRepositoryManager.getInstance(project).repositories.map { repo ->
                GitHistoryUtils.history(project, repo.root, "--max-count=1")
            }.filter { commits ->
                commits.isNotEmpty()
            }.flatMap { commits ->
                (commits.first() as GitCommit).changes
            }
        }

    suspend fun getPreviousCommitMessages(n: Int, includedChanges: List<Change>, project: Project): List<String> =
        withContext(Dispatchers.IO) {
            if (n <= 0) return@withContext emptyList()
            val repositories = affectedGitRepositories(includedChanges, project)
            if (repositories.isEmpty()) return@withContext emptyList()

            repositories
                .asSequence()
                .flatMap { repo ->
                    GitHistoryUtils.history(project, repo.root, "--max-count=$n").asSequence()
                }
                .sortedByDescending { it.commitTime }
                .take(n)
                .map { it.fullMessage }
                .toList()
        }


    suspend fun affectedGitRepositories(includedChanges: List<Change>, project: Project): Set<GitRepository> =
        withContext(Dispatchers.IO) {
            val repoManager = GitRepositoryManager.getInstance(project)
            val vcsManager = ProjectLevelVcsManager.getInstance(project)

            includedChanges.asSequence()
                .mapNotNull { changeVirtualFile(it) }
                .mapNotNull { file -> vcsManager.getVcsRootObjectFor(file)?.path }
                .mapNotNull { root -> repoManager.getRepositoryForRoot(root) }
                .toSet()
        }

    private fun changeVirtualFile(change: Change): VirtualFile? =
        change.virtualFile ?: change.afterRevision?.file?.virtualFile ?: change.beforeRevision?.file?.virtualFile

    private fun extractSvnBranchName(url: String): String? {
        val normalizedUrl = url.lowercase()

        // Standard SVN layout: repository/trunk, repository/branches/name, repository/tags/name
        return when {
            normalizedUrl.contains("/branches/") -> {
                val branchPart = url.substringAfter("/branches/")
                val endIndex = branchPart.indexOf('/')
                if (endIndex > 0) branchPart.substring(0, endIndex) else branchPart
            }

            normalizedUrl.contains("/tags/") -> {
                val tagPart = url.substringAfter("/tags/")
                val endIndex = tagPart.indexOf('/')
                if (endIndex > 0) "tag: ${tagPart.substring(0, endIndex)}" else "tag: $tagPart"
            }

            normalizedUrl.contains("/trunk") -> "trunk"
            else -> null // fallback: no branch concept available
        }
    }

    private fun isClassAvailable(className: String): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun isSvnAvailable(): Boolean {
        return isClassAvailable("org.jetbrains.idea.svn.SvnVcs") && isClassAvailable("org.jetbrains.idea.svn.SvnUtil")
    }
}
