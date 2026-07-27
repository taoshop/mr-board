# GitHub / GitLab MR & PR 真实 API 响应消息体示例

> 以下数据基于 GitHub REST API v3 和 GitLab REST API v4 的真实响应格式整理，字段与 mr-board 同步层解析逻辑对应。

---

## 1. GitHub Pull Request 响应体

### 1.1 核心 PR 详情

**Endpoint:** `GET https://api.github.com/repos/octocat/Hello-World/pulls/1347`

```json
{
  "url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
  "id": 1,
  "node_id": "MDExOlB1bGxSZXF1ZXN0MQ==",
  "html_url": "https://github.com/octocat/Hello-World/pull/1347",
  "diff_url": "https://github.com/octocat/Hello-World/pull/1347.diff",
  "patch_url": "https://github.com/octocat/Hello-World/pull/1347.patch",
  "issue_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347",
  "commits_url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347/commits",
  "review_comments_url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347/comments",
  "review_comment_url": "https://api.github.com/repos/octocat/Hello-World/pulls/comments{/number}",
  "comments_url": "https://api.github.com/repos/octocat/Hello-World/issues/1347/comments",
  "statuses_url": "https://api.github.com/repos/octocat/Hello-World/statuses/6dcb09b5b57875f334f61aebed695e2e4193db5e",
  "number": 1347,
  "state": "open",
  "locked": false,
  "title": "feat: add user authentication module",
  "user": {
    "login": "octocat",
    "id": 1,
    "node_id": "MDQ6VXNlcjE=",
    "avatar_url": "https://github.com/images/error/octocat_happy.gif",
    "gravatar_id": "",
    "url": "https://api.github.com/users/octocat",
    "html_url": "https://github.com/octocat",
    "type": "User",
    "site_admin": false
  },
  "body": "This PR introduces a new authentication module using JWT tokens.\r\n\r\n## Changes\r\n- Added `AuthController`\r\n- Added `JwtTokenProvider`\r\n- Updated security config\r\n\r\n## Checklist\r\n- [x] Unit tests passed\r\n- [ ] Integration tests pending",
  "labels": [
    {
      "id": 208045946,
      "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
      "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
      "name": "bug",
      "description": "Something isn't working",
      "color": "d73a4a",
      "default": true
    }
  ],
  "milestone": null,
  "active_lock_reason": null,
  "created_at": "2026-07-20T12:34:56Z",
  "updated_at": "2026-07-26T08:15:30Z",
  "closed_at": null,
  "merged_at": null,
  "merge_commit_sha": "e5bd3914e2e596debea16f433f57875b5b90bcd6",
  "assignee": null,
  "assignees": [],
  "requested_reviewers": [
    {
      "login": "alice-coder",
      "id": 2,
      "node_id": "MDQ6VXNlcjI=",
      "avatar_url": "https://github.com/images/error/alice_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/alice-coder",
      "html_url": "https://github.com/alice-coder",
      "type": "User",
      "site_admin": false
    },
    {
      "login": "bob-reviewer",
      "id": 3,
      "node_id": "MDQ6VXNlcjM=",
      "avatar_url": "https://github.com/images/error/bob_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/bob-reviewer",
      "html_url": "https://github.com/bob-reviewer",
      "type": "User",
      "site_admin": false
    }
  ],
  "requested_teams": [],
  "head": {
    "label": "octocat:new-feature",
    "ref": "new-feature",
    "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "user": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "type": "User",
      "site_admin": false
    },
    "repo": {
      "id": 1296269,
      "node_id": "MDEwOlJlcG9zaXRvcnkxMjk2MjY5",
      "name": "Hello-World",
      "full_name": "octocat/Hello-World",
      "owner": {
        "login": "octocat",
        "id": 1,
        "node_id": "MDQ6VXNlcjE=",
        "avatar_url": "https://github.com/images/error/octocat_happy.gif",
        "gravatar_id": "",
        "url": "https://api.github.com/users/octocat",
        "html_url": "https://github.com/octocat",
        "type": "User",
        "site_admin": false
      },
      "private": false,
      "html_url": "https://github.com/octocat/Hello-World",
      "description": "This your first repo!",
      "fork": false,
      "url": "https://api.github.com/repos/octocat/Hello-World"
    }
  },
  "base": {
    "label": "octocat:main",
    "ref": "main",
    "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "user": {
      "login": "octocat",
      "id": 1,
      "node_id": "MDQ6VXNlcjE=",
      "avatar_url": "https://github.com/images/error/octocat_happy.gif",
      "gravatar_id": "",
      "url": "https://api.github.com/users/octocat",
      "html_url": "https://github.com/octocat",
      "type": "User",
      "site_admin": false
    },
    "repo": {
      "id": 1296269,
      "node_id": "MDEwOlJlcG9zaXRvcnkxMjk2MjY5",
      "name": "Hello-World",
      "full_name": "octocat/Hello-World",
      "owner": {
        "login": "octocat",
        "id": 1,
        "node_id": "MDQ6VXNlcjE=",
        "avatar_url": "https://github.com/images/error/octocat_happy.gif",
        "gravatar_id": "",
        "url": "https://api.github.com/users/octocat",
        "html_url": "https://github.com/octocat",
        "type": "User",
        "site_admin": false
      },
      "private": false,
      "html_url": "https://github.com/octocat/Hello-World",
      "description": "This your first repo!",
      "fork": false,
      "url": "https://api.github.com/repos/octocat/Hello-World"
    }
  },
  "author_association": "OWNER",
  "auto_merge": null,
  "draft": false,
  "merged": false,
  "mergeable": true,
  "rebaseable": true,
  "mergeable_state": "clean",
  "merged_by": null,
  "comments": 5,
  "review_comments": 3,
  "maintainer_can_modify": true,
  "commits": 3,
  "additions": 128,
  "deletions": 15,
  "changed_files": 4
}
```

### 1.2 mr-board 字段映射

| mr-board 字段 | JSON Path | 示例值 |
|--------------|-----------|--------|
| `platformMrId` | `$.number` | `1347` |
| `title` | `$.title` | `"feat: add user authentication module"` |
| `description` | `$.body` | `"This PR introduces..."` |
| `authorName` | `$.user.login` | `"octocat"` |
| `authorAvatar` | `$.user.avatar_url` | `"https://github.com/images/error/octocat_happy.gif"` |
| `sourceBranch` | `$.head.ref` | `"new-feature"` |
| `targetBranch` | `$.base.ref` | `"main"` |
| `platformStatus` | `$.state` + `$.merged_at` | `"open"` |
| `mergeable` | `$.mergeable` | `true` |
| `changesCount` | `$.changed_files` | `4` |
| `additions` | `$.additions` | `128` |
| `deletions` | `$.deletions` | `15` |
| `commentsCount` | `$.comments` | `5` |
| `webUrl` | `$.html_url` | `"https://github.com/octocat/Hello-World/pull/1347"` |
| `reviewers` | `$.requested_reviewers[*].login` | `["alice-coder", "bob-reviewer"]` |
| `createdAt` | `$.created_at` | `"2026-07-20T12:34:56Z"` |
| `updatedAt` | `$.updated_at` | `"2026-07-26T08:15:30Z"` |
| `mergedAt` | `$.merged_at` | `null` |
| `closedAt` | `$.closed_at` | `null` |

---

### 1.3 已合并 PR 的响应差异

当 PR 被合并后，以下字段发生变化：

```json
{
  "state": "closed",
  "merged": true,
  "merged_at": "2026-07-25T14:22:10Z",
  "merged_by": {
    "login": "alice-coder",
    "id": 2,
    "avatar_url": "https://github.com/images/error/alice_happy.gif"
  },
  "mergeable": null,
  "mergeable_state": "unknown",
  "closed_at": "2026-07-25T14:22:10Z"
}
```

> **mr-board 处理：** `mapGitHubState()` 通过 `state == "closed" && merged_at != null` 判定为 `"merged"`。

---

### 1.4 Draft PR 的响应差异

```json
{
  "title": "Draft: feat: add user authentication module",
  "draft": true,
  "mergeable": false,
  "mergeable_state": "draft"
}
```

---

### 1.5 PR Reviews 响应

**Endpoint:** `GET https://api.github.com/repos/octocat/Hello-World/pulls/1347/reviews`

```json
[
  {
    "id": 80,
    "node_id": "MDE3OlB1bGxSZXF1ZXN0UmV2aWV3ODA=",
    "user": {
      "login": "alice-coder",
      "id": 2,
      "avatar_url": "https://github.com/images/error/alice_happy.gif"
    },
    "body": "Great work! Just a few minor comments.",
    "state": "APPROVED",
    "html_url": "https://github.com/octocat/Hello-World/pull/1347#pullrequestreview-80",
    "pull_request_url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
    "_links": {
      "html": { "href": "https://github.com/octocat/Hello-World/pull/1347#pullrequestreview-80" },
      "pull_request": { "href": "https://api.github.com/repos/octocat/Hello-World/pulls/1347" }
    },
    "submitted_at": "2026-07-22T09:10:15Z",
    "commit_id": "ecdd15a24e2c9c7f3f8b8b2e5927a2f0d0f4e72a"
  },
  {
    "id": 81,
    "node_id": "MDE3OlB1bGxSZXF1ZXN0UmV2aWV3ODE=",
    "user": {
      "login": "bob-reviewer",
      "id": 3,
      "avatar_url": "https://github.com/images/error/bob_happy.gif"
    },
    "body": "Please add more unit tests for the edge cases.",
    "state": "CHANGES_REQUESTED",
    "html_url": "https://github.com/octocat/Hello-World/pull/1347#pullrequestreview-81",
    "pull_request_url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
    "submitted_at": "2026-07-23T16:45:22Z",
    "commit_id": "ecdd15a24e2c9c7f3f8b8b2e5927a2f0d0f4e72a"
  },
  {
    "id": 82,
    "node_id": "MDE3OlB1bGxSZXF1ZXN0UmV2aWV3ODI=",
    "user": {
      "login": "charlie-dev",
      "id": 4,
      "avatar_url": "https://github.com/images/error/charlie_happy.gif"
    },
    "body": "LGTM after the changes are made.",
    "state": "COMMENTED",
    "html_url": "https://github.com/octocat/Hello-World/pull/1347#pullrequestreview-82",
    "pull_request_url": "https://api.github.com/repos/octocat/Hello-World/pulls/1347",
    "submitted_at": "2026-07-24T11:20:00Z",
    "commit_id": "ecdd15a24e2c9c7f3f8b8b2e5927a2f0d0f4e72a"
  }
]
```

> **mr-board 处理：**
> - `APPROVED` → `hasApproved = true`
> - `CHANGES_REQUESTED` → `hasChangesRequested = true`
> - `COMMENTED` → `hasMeaningfulReview = false`（纯评论不算实质评审）
> - 最终结果：`hasChangesRequested = true` → `approvalStatus = "reviewing"`

---

### 1.6 Check Runs 响应

**Endpoint:** `GET https://api.github.com/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e/check-runs`

```json
{
  "total_count": 3,
  "check_runs": [
    {
      "id": 128620228,
      "node_id": "MDg6Q2hlY2tSdW4xMjg2MjAyMjg=",
      "head_sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "external_id": "",
      "url": "https://api.github.com/repos/octocat/Hello-World/check-runs/128620228",
      "html_url": "https://github.com/octocat/Hello-World/runs/128620228",
      "details_url": "https://github.com/octocat/Hello-World/runs/128620228",
      "status": "completed",
      "conclusion": "success",
      "started_at": "2026-07-26T08:10:00Z",
      "completed_at": "2026-07-26T08:12:30Z",
      "name": "build",
      "check_suite": {
        "id": 5,
        "node_id": "MDEwOkNoZWNrU3VpdGU1"
      }
    },
    {
      "id": 128620229,
      "node_id": "MDg6Q2hlY2tSdW4xMjg2MjAyMjk=",
      "head_sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "status": "completed",
      "conclusion": "failure",
      "started_at": "2026-07-26T08:10:00Z",
      "completed_at": "2026-07-26T08:15:00Z",
      "name": "test",
      "html_url": "https://github.com/octocat/Hello-World/runs/128620229",
      "check_suite": {
        "id": 5
      }
    },
    {
      "id": 128620230,
      "node_id": "MDg6Q2hlY2tSdW4xMjg2MjAyMzA=",
      "head_sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "status": "in_progress",
      "conclusion": null,
      "started_at": "2026-07-26T08:16:00Z",
      "name": "lint",
      "html_url": "https://github.com/octocat/Hello-World/runs/128620230",
      "check_suite": {
        "id": 5
      }
    }
  ]
}
```

> **mr-board 处理：**
> - `test`（`status=completed, conclusion=failure`）→ `ciStatus = "failed"`
> - 整个 PR 的 CI 状态取所有 check runs 的**最差状态**：`failed`

---

## 2. GitLab Merge Request 响应体

### 2.1 核心 MR 详情

**Endpoint:** `GET https://gitlab.example.com/api/v4/projects/42/merge_requests/1347`

```json
{
  "id": 1324,
  "iid": 1347,
  "project_id": 42,
  "title": "feat: add user authentication module",
  "description": "This MR introduces a new authentication module using JWT tokens.\n\n## Changes\n- Added `AuthController`\n- Added `JwtTokenProvider`\n- Updated security config",
  "state": "opened",
  "created_at": "2026-07-20T12:34:56.000Z",
  "updated_at": "2026-07-26T08:15:30.000Z",
  "merged_by": null,
  "merge_user": null,
  "merged_at": null,
  "closed_by": null,
  "closed_at": null,
  "target_branch": "main",
  "source_branch": "new-feature",
  "user_notes_count": 8,
  "upvotes": 2,
  "downvotes": 0,
  "author": {
    "id": 1,
    "username": "octocat",
    "name": "Octocat Developer",
    "state": "active",
    "avatar_url": "https://gitlab.example.com/uploads/user/avatar/1/octocat.png",
    "web_url": "https://gitlab.example.com/octocat"
  },
  "assignees": [],
  "assignee": null,
  "reviewers": [
    {
      "id": 2,
      "username": "alice-coder",
      "name": "Alice Coder",
      "state": "active",
      "avatar_url": "https://gitlab.example.com/uploads/user/avatar/2/alice.png",
      "web_url": "https://gitlab.example.com/alice-coder"
    },
    {
      "id": 3,
      "username": "bob-reviewer",
      "name": "Bob Reviewer",
      "state": "active",
      "avatar_url": "https://gitlab.example.com/uploads/user/avatar/3/bob.png",
      "web_url": "https://gitlab.example.com/bob-reviewer"
    }
  ],
  "source_project_id": 42,
  "target_project_id": 42,
  "labels": ["feature", "security"],
  "draft": false,
  "work_in_progress": false,
  "milestone": null,
  "merge_when_pipeline_succeeds": false,
  "merge_status": "can_be_merged",
  "detailed_merge_status": "mergeable",
  "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
  "merge_commit_sha": null,
  "squash_commit_sha": null,
  "discussion_locked": null,
  "should_remove_source_branch": false,
  "force_remove_source_branch": false,
  "allow_collaboration": false,
  "allow_maintainer_to_push": false,
  "web_url": "https://gitlab.example.com/octocat/hello-world/-/merge_requests/1347",
  "references": {
    "short": "!1347",
    "relative": "!1347",
    "full": "octocat/hello-world!1347"
  },
  "time_stats": {
    "time_estimate": 0,
    "total_time_spent": 0,
    "human_time_estimate": null,
    "human_total_time_spent": null
  },
  "squash": false,
  "subscribed": true,
  "changes_count": "4",
  "merged_by": null,
  "closed_by": null,
  "task_completion_status": {
    "count": 3,
    "completed_count": 2
  },
  "has_conflicts": false,
  "blocking_discussions_resolved": true,
  "overflow": false
}
```

### 2.2 mr-board 字段映射

| mr-board 字段 | JSON Path | 示例值 |
|--------------|-----------|--------|
| `platformMrId` | `$.iid` | `1347` |
| `title` | `$.title` | `"feat: add user authentication module"` |
| `description` | `$.description` | `"This MR introduces..."` |
| `authorName` | `$.author.username` | `"octocat"` |
| `authorAvatar` | `$.author.avatar_url` | `"https://gitlab.example.com/uploads/user/avatar/1/octocat.png"` |
| `sourceBranch` | `$.source_branch` | `"new-feature"` |
| `targetBranch` | `$.target_branch` | `"main"` |
| `platformStatus` | `$.state` | `"opened"` |
| `hasConflict` | `$.has_conflicts` | `false` |
| `mergeable` | `$.merge_status == "can_be_merged"` | `true` |
| `changesCount` | `$.changes_count` | `4` |
| `commentsCount` | `$.user_notes_count` | `8` |
| `labels` | `$.labels` | `["feature", "security"]` |
| `webUrl` | `$.web_url` | `"https://gitlab.example.com/.../-/merge_requests/1347"` |
| `reviewers` | `$.reviewers[*].username` | `["alice-coder", "bob-reviewer"]` |
| `createdAt` | `$.created_at` | `"2026-07-20T12:34:56.000Z"` |
| `updatedAt` | `$.updated_at` | `"2026-07-26T08:15:30.000Z"` |
| `mergedAt` | `$.merged_at` | `null` |
| `closedAt` | `$.closed_at` | `null` |

---

### 2.3 已合并 MR 的响应差异

```json
{
  "state": "merged",
  "merged_at": "2026-07-25T14:22:10.000Z",
  "merged_by": {
    "id": 2,
    "username": "alice-coder",
    "name": "Alice Coder",
    "avatar_url": "https://gitlab.example.com/uploads/user/avatar/2/alice.png"
  },
  "merge_commit_sha": "e5bd3914e2e596debea16f433f57875b5b90bcd6",
  "merge_status": "can_be_merged",
  "detailed_merge_status": "merged"
}
```

---

### 2.4 WIP MR 的响应差异

```json
{
  "title": "WIP: feat: add user authentication module",
  "draft": false,
  "work_in_progress": true,
  "merge_status": "unchecked",
  "detailed_merge_status": "not_open"
}
```

---

### 2.5 MR Approvals 响应

**Endpoint:** `GET https://gitlab.example.com/api/v4/projects/42/merge_requests/1347/approvals`

```json
{
  "id": 1,
  "iid": 1347,
  "project_id": 42,
  "title": "feat: add user authentication module",
  "description": "This MR introduces...",
  "state": "opened",
  "created_at": "2026-07-20T12:34:56.000Z",
  "updated_at": "2026-07-26T08:15:30.000Z",
  "merge_status": "can_be_merged",
  "approved": false,
  "approvals_required": 2,
  "approvals_left": 1,
  "require_password_to_approve": false,
  "approved_by": [
    {
      "user": {
        "id": 2,
        "username": "alice-coder",
        "name": "Alice Coder",
        "state": "active",
        "avatar_url": "https://gitlab.example.com/uploads/user/avatar/2/alice.png"
      }
    }
  ],
  "suggested_approvers": [],
  "rules": [
    {
      "id": 1,
      "name": "Code Review",
      "rule_type": "regular",
      "approvals_required": 2,
      "approved_by": [
        {
          "id": 2,
          "username": "alice-coder",
          "name": "Alice Coder"
        }
      ],
      "contains_hidden_groups": false,
      "source_rule": null,
      "users": [
        {
          "id": 2,
          "username": "alice-coder"
        },
        {
          "id": 3,
          "username": "bob-reviewer"
        }
      ]
    }
  ]
}
```

> **mr-board 处理：**
> - `approved = false` 但 `approved_by` 非空 → `approvalStatus = "reviewing"`
> - 还需要 1 人 approve 才能满足 `approvals_required = 2`

---

### 2.6 MR Pipelines 响应

**Endpoint:** `GET https://gitlab.example.com/api/v4/projects/42/merge_requests/1347/pipelines`

```json
[
  {
    "id": 47,
    "iid": 12,
    "project_id": 42,
    "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "ref": "new-feature",
    "status": "failed",
    "source": "merge_request_event",
    "created_at": "2026-07-26T08:10:00.000Z",
    "updated_at": "2026-07-26T08:20:00.000Z",
    "web_url": "https://gitlab.example.com/octocat/hello-world/-/pipelines/47"
  }
]
```

**Jobs 详情：**

**Endpoint:** `GET https://gitlab.example.com/api/v4/projects/42/pipelines/47/jobs`

```json
[
  {
    "id": 101,
    "stage": "build",
    "name": "compile",
    "status": "success",
    "created_at": "2026-07-26T08:10:05.000Z",
    "started_at": "2026-07-26T08:10:10.000Z",
    "finished_at": "2026-07-26T08:12:30.000Z",
    "duration": 140.5,
    "queued_duration": 4.2,
    "failure_reason": null,
    "web_url": "https://gitlab.example.com/octocat/hello-world/-/jobs/101"
  },
  {
    "id": 102,
    "stage": "test",
    "name": "unit-test",
    "status": "failed",
    "created_at": "2026-07-26T08:12:35.000Z",
    "started_at": "2026-07-26T08:12:40.000Z",
    "finished_at": "2026-07-26T08:15:00.000Z",
    "duration": 140.0,
    "queued_duration": 2.1,
    "failure_reason": "script_failure",
    "web_url": "https://gitlab.example.com/octocat/hello-world/-/jobs/102",
    "allow_failure": false
  },
  {
    "id": 103,
    "stage": "test",
    "name": "integration-test",
    "status": "pending",
    "created_at": "2026-07-26T08:15:05.000Z",
    "started_at": null,
    "finished_at": null,
    "duration": null,
    "queued_duration": null,
    "failure_reason": null,
    "web_url": "https://gitlab.example.com/octocat/hello-world/-/jobs/103",
    "allow_failure": true
  }
]
```

> **mr-board 处理：**
> - `unit-test`（`status=failed, allow_failure=false`）→ 整体 pipeline `status = "failed"` → `ciStatus = "failed"`
> - `integration-test`（`status=pending`）但前置 job 已失败，实际不会执行

---

## 3. 差异速查：同一 MR 在 GitHub vs GitLab 中的字段差异

| 语义 | GitHub 字段 | GitLab 字段 |
|------|------------|-------------|
| MR/PR 编号 | `number` | `iid` |
| 作者 | `user.login` | `author.username` |
| 源分支 | `head.ref` | `source_branch` |
| 目标分支 | `base.ref` | `target_branch` |
| 平台状态 | `state`（需结合 `merged_at` 区分 merged） | `state`（直接返回 `merged`） |
| 草稿标识 | `draft`（boolean） | `work_in_progress` / `draft` |
| 代码冲突 | 无直接字段（需 Compare API） | `has_conflicts`（boolean） |
| 可合并状态 | `mergeable`（boolean）+ `mergeable_state` | `merge_status`（string） |
| Reviewer 列表 | `requested_reviewers[*].login` | `reviewers[*].username` |
| Reviewer ID | `id`（全局数字） | `id`（全局数字） |
| 评论数 | `comments` | `user_notes_count` |
| 变更文件数 | `changed_files` | `changes_count`（string! 需转 int） |
| CI 状态 | Check Runs（`status` + `conclusion`） | Pipeline Jobs（单一 `status`） |
| Approval | reviews[].state（个人行为） | approvals.approved（规则结论） |
| 更新时间 | `updated_at` | `updated_at` |
| 关闭者 | 无 | `closed_by.username` |
| 合并者 | `merged_by.login` | `merged_by.username` |

---

*文档完*
