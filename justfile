# Deployment orchestration for the Mac Mini.
#
# This file is hand-written and committed. The version-dependent build recipes live in `build.just`,
# which is GENERATED from build.just.template by Maven — see that template for why.
#
# Normal deploy on the Mac Mini, from the repo root:
#   just pull      # checkout the highest version tag
#   just build     # build the jar and assemble deployment/bin
#   just start     # run it in the foreground
# or `just doit` for all three.

import 'build.just'

# List available recipes (first recipe in this file = what a bare `just` runs)
default:
  @just --list

# Build the jar and assemble the run directory
build: build_release prepare_release

# Assemble deployment/bin — the directory pbs is actually started from.
# Deliberately not target/: `mvn clean` must not be able to delete the jar of a running instance.
[doc('Assemble deployment/bin, the directory pbs is started from')]
prepare_release:
  rm -rf deployment/bin
  mkdir -p deployment/bin
  mkdir -p deployment/logs
  cp -Rp release/ deployment/bin
  cp -p deployment/start-pbs.sh deployment/bin
  chmod +x deployment/bin/start-pbs.sh

# Start pbs in the foreground
start:
  cd deployment/bin && ./start-pbs.sh {{justfile_directory()}}/deployment

# Follow the application log
logs:
  tail -f deployment/logs/pbs.log

# Pull the latest tagged version
pull:
    #!/usr/bin/env bash
    set -euo pipefail

    echo "Fetching latest tags..."
    git fetch --tags

    # Get the latest semantic version tag
    LATEST_TAG=$(git tag --sort=-version:refname | head -n1)

    if [ -z "$LATEST_TAG" ]; then
        echo "No tags found in repository"
        exit 1
    fi

    echo "Latest tag found: $LATEST_TAG"
    echo "Checking out to $LATEST_TAG..."
    git checkout $LATEST_TAG

# Show the latest available tag
show_latest_tag:
    #!/usr/bin/env bash
    git fetch --tags
    LATEST_TAG=$(git tag --sort=-version:refname | head -n1)
    echo "Latest tag: $LATEST_TAG"

# Each step re-invokes `just` as a new process on purpose: `pull` rewrites justfile/build.just, and
# just parses them once at startup — so a single-process chain would build the checked-out source
# using the PREVIOUS version's jar name.
[doc('Pull the latest tag, build it and start')]
doit:
    #!/usr/bin/env bash
    set -euo pipefail
    just pull
    just build
    just start
