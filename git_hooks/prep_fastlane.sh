#!/bin/sh

gitTopLevel=$(git rev-parse --show-toplevel)

for tag in $(git tagd); do
  # do something like: echo $databaseName
  versionCode=$(git show $tag:app/build.gradle.kts | grep versionCode | tr -s ' \n' | sed -e 's/versionCode\s*=\s*//' | xargs)

  printf "%6s: $versionCode\n" $tag

  commitMessage=$(git show $tag -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')

  # Signed\-off\-by:\s*.*

  echo "$commitMessage" > "$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$versionCode.txt"
  echo "saving to '$gitTopLevel/fastlane/metadata/android/en-US/changelogs/$versionCode.txt'"
  echo
done