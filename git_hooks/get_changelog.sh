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

echo "Generating Changelog..."
while IFS= read -r body && IFS= read -r subject; do
  subject_trimmed=$(echo "$subject" | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed 's/__END__//' | sed -e 's/^[^a-zA-Z0-9]*//')
  body_trimmed=$(echo "$body" | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed 's/__END__//' | sed -e 's/^[^a-zA-Z0-9]*//')

  [[ -n "$subject_trimmed" ]] && echo "* $subject_trimmed"
  [[ -n "$body_trimmed" ]] && echo "- $body_trimmed"

  subjects+=("$subject_trimmed")
  bodies+=("$body_trimmed")
done < <(git log "$previousTag".."$latestTag" --pretty=format:"%s%n%b%n__END__")

changeLogs=${#subjects[@]}

if [ $changeLogs -gt 0 ]; then
  echo "saving to '$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt'..."

  echo "" > "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"

  for subject in "${subjects[@]}"; do
    [[ -n "$subject" ]] && echo "* $subject" >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
  done

  for body in "${bodies[@]}"; do
    [[ -n "$body" ]] && echo "- $body" >> "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
  done

  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")
  if [ -n "$isCurrentCommitOnRemote" ]; then
    newVersionName="${newTag#v}"

    echo "commit '$currentCommitHash' is on the remote branch, creating a new change log commit..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/"
    git commit -sm "updated $changeLogs change logs(s)"
  else
    echo "commit '$currentCommitHash' is not on the remote branch, amending..."
    echo

    git add "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/"
    git commit --amend --no-edit
  fi
fi
