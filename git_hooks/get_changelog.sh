#!/bin/bash

# Get the latest tag
gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
previousTag=$(git tag | sort | tail -n 2 | head -n 1)
latestTag=$(git describe --tags $(git rev-list --tags --max-count=1))
previousVersionCode=$(git show "$previousTag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
latestVersionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
changeLogs=0
subjects=()
bodies=()

echo "Previous Tag: $previousTag, previousVersionCode: $previousVersionCode"
echo "Latest Tag: $latestTag, latestVersionCode: $latestVersionCode"

if [ -z "$latestTag" ] || [ -z "$previousTag" ]; then
  echo "No tags found in the repository."
  exit 1
fi

echo "Generating Changelog between $previousTag and $latestTag..."
while read commit_hash
do
  subject=$(git log --format=%s -n 1 $commit_hash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')
  body=$(git log --format=%b -n 1 $commit_hash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')

  subjects+=("$subject")
  bodies+=("$body")
  changeLogs=${#subjects[@]}

  if [ $changeLogs -eq 1 ]; then
    echo "saving to '$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt'..."
  fi

  echo "Commit: $commit_hash"
  echo "* $subject" | tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"

  readarray -t lines <<< "$body"
  lineCount=${#lines[@]}
  for line in "${lines[@]}"; do
    if [[ -n "$line" ]]; then
      if [ $lineCount -gt 1 ]; then
        echo "   > - $line" | tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"
      else
        echo "   > $line" | tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"
      fi
    else
      if [ $lineCount -eq 1 ]; then
        echo "   >" | tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"
      fi
    fi
  done
  echo "------------------------------"
done < <(git log "$previousTag".."$latestTag" --pretty=format:"%H")

if [ $changeLogs -gt 0 ]; then
  echo "$changeLogs change log(s) saved to '$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt'!"
  echo | tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"
  echo "**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$previousTag...$latestTag" |\
  tee -a "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$latestVersionCode.txt"

  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")
  if [ -n "$isCurrentCommitOnRemote" ]; then
    newVersionName="${newTag#v}"

    echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/"
    git commit -sm "updated $changeLogs change log(s)"
  else
    echo "commit '$currentCommitHash' is not on the remote branch, amending..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/"
    git commit --amend --no-edit
  fi
else
  echo "No / $changeLogs change log(s) found between $previousTag and $latestTag"
fi

