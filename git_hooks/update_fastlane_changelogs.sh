#!/bin/bash

gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
changeLogs=0

for tag in $(git tag)
do
  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo "checking out tag: $tag ..."

  versionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)

  echo "$tag: versionCode = $versionCode"

  previousCommitIndex=1
  commitMessage="$(git show "$tag" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')"

  echo "-----------------------------------------------------------------"
  echo "commit message@$tag:"
  echo "$commitMessage"

  while echo "$commitMessage" | grep -qE 'updated tag v[0-9]+(?:.[0-9]+)?(?:.[0-9]+)?'
  do
      ((previousCommitIndex+=1))
      previousCommitMessage="$(git show "$tag~$previousCommitIndex" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')"
      commitMessage=$previousCommitMessage
  done

  if [ -n "$previousCommitMessage" ]; then
    echo "-----------------------------------------------------------------"
    echo "previous commit message@$tag~$previousCommitIndex:"
    echo "$previousCommitMessage"
    echo "-----------------------------------------------------------------"
  else
    echo "-----------------------------------------------------------------"
  fi

  echo "$commitMessage" > "$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt"
  echo "saving to '$gitTopLevel/fastlane/metadata/android/en-US/changeLogs/$versionCode.txt'"
  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo

  ((changeLogs+=1))
done

if [ $changeLogs -gt 0 ]; then
  currentCommitHash=$(git rev-parse HEAD)
  isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")

  echo

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
