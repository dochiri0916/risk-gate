"""Resolve or create the pull request evaluated by the reusable workflow."""

import json
import os
import subprocess
import sys


def gh(*args):
    return subprocess.run(["gh", *args], text=True, capture_output=True)


def find_pr(repository, branch):
    result = gh(
        "api", f"repos/{repository}/pulls", "--method", "GET",
        "-f", "state=open", "-f", f"head={repository.split('/')[0]}:{branch}",
        "--jq", ".[0] // empty",
    )
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None


def resolve(env):
    if env["EVENT_NAME"] == "pull_request":
        with open(env["EVENT_PATH"], encoding="utf-8") as event_file:
            pr = json.load(event_file)["pull_request"]
        return pr["base"]["ref"], pr["number"], pr["head"]["sha"]

    repository = env["REPOSITORY"]
    branch = env["BRANCH"]
    base = env["BASE_BRANCH"]
    pr = find_pr(repository, branch)
    if pr is None:
        result = gh(
            "api", f"repos/{repository}/pulls", "--method", "POST",
            "-f", f"head={branch}", "-f", f"base={base}",
            "-f", f"title={branch}",
            "-f", "body=Automatically created by Risk Gate.",
        )
        if result.returncode:
            # Another run may have created the PR after the lookup.
            pr = find_pr(repository, branch)
            if pr is None:
                raise RuntimeError(result.stderr.strip())
        else:
            pr = json.loads(result.stdout)
    return pr["base"]["ref"], pr["number"], env["HEAD_SHA"]


def main():
    try:
        base, number, sha = resolve(os.environ)
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"BASE_REF={base}\nPR_NUMBER={number}\nHEAD_SHA={sha}\n")
    except (KeyError, ValueError, RuntimeError) as error:
        print(f"PR context: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
