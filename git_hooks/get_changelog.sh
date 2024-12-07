#!/bin/bash

# Get the latest tag
gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
previousTag=$(git tag | sort | tail -n 2 | head -n 1)
latestTag=$(git describe --tags $(git rev-list --tags --max-count=1))
commitHashesBetweenTags=$(git log $previousTag..$latestTag --pretty=format:"%H")
commitHashCount=$(echo "$commitHashesBetweenTags" | wc -l)
previousVersionCode=$(git show "$previousTag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
latestVersionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
changelogsPath="$gitTopLevel/fastlane/metadata/android/en-US/changelogs"
changelogs=0
subjects=()
bodies=()

echo "Previous Tag: $previousTag, previousVersionCode: $previousVersionCode"
echo "Latest Tag: $latestTag, latestVersionCode: $latestVersionCode"

if [ -z "$latestTag" ] || [ -z "$previousTag" ]; then
  echo "No tags found in the repository."
  exit 1
fi

if [ ! -d "$changelogsPath" ]; then
  echo "Creating changelogs folder..."
  mkdir -p "$changelogsPath"
fi

if [ $commitHashCount -gt 0 ] && [ -f "$changelogsPath/$latestVersionCode.txt" ]; then
  echo "'$changelogsPath/$latestVersionCode.txt' already exists, deleting..."
  rm "$changelogsPath/$latestVersionCode.txt"
fi

echo "Generating Changelog between $previousTag and $latestTag..."
for commitHash in $commitHashesBetweenTags
do
  subject=$(git log --format=%s -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')
  body=$(git log --format=%b -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')

  subjects+=("$subject")
  bodies+=("$body")
  changelogs=${#subjects[@]}

  if [ $changelogs -eq 1 ]; then
    echo "saving to '$changelogsPath/$latestVersionCode.txt'..."
  fi

  echo "Commit: $commitHash"
  echo "* $subject" | tee -a "$changelogsPath/$latestVersionCode.txt"

  readarray -t lines <<< "$body"
  lineCount=${#lines[@]}
  for line in "${lines[@]}"; do
    if [[ -n "$line" ]]; then
      if [ $lineCount -gt 1 ]; then
        echo "   > - $line" | tee -a "$changelogsPath/$latestVersionCode.txt"
      else
        echo "   > $line" | tee -a "$changelogsPath/$latestVersionCode.txt"
      fi
    else
      if [ $lineCount -eq 1 ]; then
        echo "   >" | tee -a "$changelogsPath/$latestVersionCode.txt"
      fi
    fi
  done
  echo "------------------------------"
done

if [ $changelogs -gt 0 ]; then
  echo "$changelogs changelog(s) saved to '$changelogsPath/$latestVersionCode.txt'!"
  echo | tee -a "$changelogsPath/$latestVersionCode.txt"
  echo "**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$previousTag...$latestTag" |\
  tee -a "$changelogsPath/$latestVersionCode.txt"

  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")
  if [ -n "$isCurrentCommitOnRemote" ]; then
    echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
    echo

    git add "$changelogsPath/"
    git commit -sm "updated $changelogs change log(s)"
  else
    echo "commit '$currentCommitHash' is not on the remote branch, amending..."
    echo

    git add "$changelogsPath/"
    git commit --amend --no-edit
  fi
else
  echo "No / $changelogs change log(s) found between $previousTag and $latestTag"
fi