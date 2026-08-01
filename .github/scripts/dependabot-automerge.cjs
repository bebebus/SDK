'use strict';

const REQUIRED_WORKFLOWS = ['ci', 'CodeQL'];
const MAX_DIFF_LINES = 1500;
const ALLOWED_FILES = Object.freeze({
  javascript: new Set(['nodejs/package.json', 'nodejs/package-lock.json']),
  php: new Set(['php/composer.json', 'php/composer.lock']),
  python: new Set(['python/pyproject.toml', 'python/requirements-ci.txt']),
  java: new Set(['java/pom.xml']),
});

function parseStableVersion(value) {
  if (!/^\d+\.\d+\.\d+$/.test(value)) return null;
  return value.split('.').map(Number);
}

function parseUpgrade(title) {
  if (!title.startsWith('chore(deps-dev):')) return null;

  const match = title.match(/\bfrom\s+v?(\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?)\s+to\s+v?(\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?)(?:\s|$)/i);
  if (!match) return null;

  const from = parseStableVersion(match[1]);
  const to = parseStableVersion(match[2]);
  if (!from || !to) return null;

  const delta = to[0] - from[0] || to[1] - from[1] || to[2] - from[2];
  if (delta <= 0 || from[0] !== to[0]) return null;

  return { from, to };
}

function classifyFiles(files, labels) {
  const ecosystems = Object.keys(ALLOWED_FILES).filter((label) => labels.has(label));
  if (ecosystems.length !== 1 || files.length === 0 || files.length > 2) return null;

  const ecosystem = ecosystems[0];
  const allowed = ALLOWED_FILES[ecosystem];
  if (files.some((file) => !allowed.has(file.filename))) return null;

  const diffLines = files.reduce(
    (sum, file) => sum + Number(file.additions || 0) + Number(file.deletions || 0),
    0,
  );
  if (diffLines > MAX_DIFF_LINES) return null;

  return ecosystem;
}

function latestRunsByName(runs) {
  const latest = new Map();
  for (const run of runs) {
    if (run.event !== 'pull_request') continue;
    const previous = latest.get(run.name);
    if (!previous || Number(run.id) > Number(previous.id)) latest.set(run.name, run);
  }
  return latest;
}

function requiredChecksPassed(runs) {
  const latest = latestRunsByName(runs);
  return REQUIRED_WORKFLOWS.every((name) => {
    const run = latest.get(name);
    return run && run.status === 'completed' && run.conclusion === 'success';
  });
}

function allCheckRunsPassed(checkRuns) {
  const greenConclusions = new Set(['success', 'neutral', 'skipped']);
  return (
    checkRuns.length > 0 &&
    checkRuns.every(
      (check) => check.status === 'completed' && greenConclusions.has(check.conclusion),
    )
  );
}

async function candidateNumbers({ github, context }) {
  if (context.eventName === 'workflow_run') {
    const pullRequests = context.payload.workflow_run?.pull_requests || [];
    return [...new Set(pullRequests.map((pr) => Number(pr.number)).filter(Number.isInteger))];
  }

  const { owner, repo } = context.repo;
  const pulls = await github.paginate(github.rest.pulls.list, {
    owner,
    repo,
    state: 'open',
    base: 'main',
    per_page: 100,
  });

  return pulls
    .filter((pr) => pr.user?.login === 'dependabot[bot]')
    .sort((left, right) => left.number - right.number)
    .map((pr) => pr.number);
}

async function evaluatePullRequest({ github, context, number }) {
  const { owner, repo } = context.repo;
  const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: number });

  if (
    pr.state !== 'open' ||
    pr.draft ||
    pr.user?.login !== 'dependabot[bot]' ||
    pr.base?.ref !== 'main' ||
    pr.head?.repo?.full_name !== `${owner}/${repo}` ||
    !pr.head?.ref?.startsWith('dependabot/') ||
    pr.mergeable !== true ||
    pr.mergeable_state !== 'clean' ||
    !parseUpgrade(pr.title)
  ) {
    return { eligible: false, reason: '身份、分支、版本跨度或可合并状态不符合白名单' };
  }

  const labels = new Set((pr.labels || []).map((label) => label.name));
  if (!labels.has('dependencies') || labels.has('github_actions')) {
    return { eligible: false, reason: '缺少 dependencies 标签或属于 GitHub Actions 更新' };
  }

  const files = await github.paginate(github.rest.pulls.listFiles, {
    owner,
    repo,
    pull_number: number,
    per_page: 100,
  });
  const ecosystem = classifyFiles(files, labels);
  if (!ecosystem) {
    return { eligible: false, reason: '变更文件、语言标签或 diff 大小不符合白名单' };
  }

  const reviews = await github.paginate(github.rest.pulls.listReviews, {
    owner,
    repo,
    pull_number: number,
    per_page: 100,
  });
  if (reviews.some((review) => review.state === 'CHANGES_REQUESTED')) {
    return { eligible: false, reason: '存在 Changes requested review' };
  }

  const runs = await github.paginate(github.rest.actions.listWorkflowRunsForRepo, {
    owner,
    repo,
    head_sha: pr.head.sha,
    per_page: 100,
  });
  if (!requiredChecksPassed(runs)) {
    return { eligible: false, reason: '当前 head SHA 的 ci 与 CodeQL 尚未全部成功' };
  }

  const { data: checks } = await github.rest.checks.listForRef({
    owner,
    repo,
    ref: pr.head.sha,
    filter: 'latest',
    per_page: 100,
  });
  if (!allCheckRunsPassed(checks.check_runs || [])) {
    return { eligible: false, reason: '当前 head SHA 仍有未完成或未通过的 check run' };
  }

  return { eligible: true, pr, ecosystem };
}

async function main({ github, context, core }) {
  const numbers = await candidateNumbers({ github, context });
  if (numbers.length === 0) {
    core.info('没有关联或待处理的 Dependabot PR。');
    return;
  }

  for (const number of numbers) {
    const result = await evaluatePullRequest({ github, context, number });
    if (!result.eligible) {
      core.info(`#${number} 跳过：${result.reason}`);
      continue;
    }

    const { owner, repo } = context.repo;
    const response = await github.rest.pulls.merge({
      owner,
      repo,
      pull_number: number,
      sha: result.pr.head.sha,
      merge_method: 'squash',
      commit_title: result.pr.title,
      commit_message: `由 Dependabot 低风险自动维护流程合并（${result.ecosystem}，ci 与 CodeQL 已通过）。`,
    });

    if (!response.data.merged) {
      throw new Error(`#${number} 未能合并：${response.data.message || 'GitHub 未返回原因'}`);
    }

    core.notice(`#${number} 已通过严格白名单校验并 squash 合并。`);
    return;
  }

  core.info('本次没有符合严格白名单的 PR。');
}

module.exports = main;
module.exports.parseUpgrade = parseUpgrade;
module.exports.classifyFiles = classifyFiles;
module.exports.requiredChecksPassed = requiredChecksPassed;
module.exports.allCheckRunsPassed = allCheckRunsPassed;
