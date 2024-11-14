#!/bin/bash

# Get the latest tag
gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
previousTag=$(git tag | sort -V | tail -n 2 | head -n 1)
latestTag=$(git describe --tags $(git rev-list --tags --max-count=1))
versionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
changeLogs=0
subjects=()
bodies=()

echo "Latest Tag: $latestTag, versionCode: $versionCode"

if [ -z "$latestTag" ] || [ -z "$previousTag" ]; then
  echo "No tags found in the repository."
  exit 1
fi

echo "Generating Changelog between $previousTag and $latestTag..."
while read commit_hash
do
  subject=$(git log --format=%s -n 1 $commit_hash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')
  body=$(git log --format=%b -n 1 $commit_hash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')

  echo "Commit: $commit_hash"
  echo "* $subject"
  subjects+=("$subject")
  if [[ -n "$body" ]]; then
    echo "- $body"
    bodies+=("$body")
  fi
  echo "------------------------------"
done < <(git log "$previousTag".."$latestTag" --pretty=format:"%H")

changeLogs=${#subjects[@]}

if [ $changeLogs -gt 0 ]; then
  echo "saving to '$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt'..."

  echo "" > "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"

  for index in $(seq ${#subjects[@]} -1 1); do
    subject=${subjects[$index-1]}
    if [[ -n "$subject" ]]; then
      echo "* $subject" | sed '2,$s/^/  /' >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
    fi
  done

  echo >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"

  for index in $(seq ${#bodies[@]} -1 1); do
    body=${bodies[$index-1]}
    if [[ -n "$body" ]]; then
      echo "- $body" | sed '2,$s/^/  /' >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
      echo >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
    fi
  done

  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")
  if [ -n "$isCurrentCommitOnRemote" ]; then
    newVersionName="${newTag#v}"

    echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/"
    git commit -sm "updated $changeLogs change log(s)"
  else
    echo "commit '$currentCommitHash' is not on the remote branch, amending..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/"
    git commit --amend --no-edit
  fi
else
  echo "No / $changeLogs change log(s) found between $previousTag and $latestTag"
fi

