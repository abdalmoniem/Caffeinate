#!/bin/bash

gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
changelogsPath="$gitTopLevel/fastlane/metadata/android/en-US/changelogs"
tags=($(git tag))
changelogs=0

isWriteChanges=false
isCommitChanges=false

# Parse command-line flags
while [[ $# -gt 0 ]]; do
    case "$1" in
        --write_changes)
            isWriteChanges=true
            shift # Move to the next argument
            ;;
        --commit_changes)
            isCommitChanges=true
            shift # Move to the next argument
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--write_changes] [--commit_changes]"
            exit 1
            ;;
    esac
done

if [[ "$isWriteChanges" == true ]]; then
  if [ ! -d "$changelogsPath" ]; then
    echo "Creating changelogs folder..."
    mkdir -p "$changelogsPath"
  fi
fi

for index in "${!tags[@]}"; do
  tag="${tags[$index]}"
  previousTag="${tags[$((index-1))]}"  # Get the previous tag, or skip if i is 0 (first tag)

  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  versionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
  commitMessage="$(git show "$tag" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')"
  if [[ -n "$previousTag" ]]; then
    fullChangelog="**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$previousTag...$tag"
  else
    fullChangelog="**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/commits/$tag"
  fi

  echo "commit message@$tag:"
  echo "$commitMessage"
  echo
  echo "$fullChangelog"

  if [[ "$isWriteChanges" == true ]]; then
    echo "saving to '$changelogsPath/$versionCode.txt'..."
    echo "$commitMessage" > "$changelogsPath/$versionCode.txt"
    echo >> "$changelogsPath/$versionCode.txt"
    echo "$fullChangelog" >> "$changelogsPath/$versionCode.txt"
  fi
  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo

  ((changelogs+=1))
done

if [[ $changelogs -gt 0 && "$isCommitChanges" == true ]]; then
  echo "$changelogs changelog(s) saved!"
  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")

  echo

  if [ -n "$isCurrentCommitOnRemote" ]; then
    newVersionName="${newTag#v}"

    echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
    echo

    git add "$changelogsPath"
    git commit -sm "updated $changelogs change logs(s)"
  else
    echo "commit '$currentCommitHash' is not on the remote branch, amending..."
    echo

    git add "$changelogsPath"
    git commit --amend --no-edit
  fi
fi
