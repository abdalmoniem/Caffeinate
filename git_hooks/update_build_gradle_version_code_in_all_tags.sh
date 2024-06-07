#!/bin/bash

startingVersion=1

HEAD=$(git rev-parse HEAD)
currentBranch=$(git rev-parse --abbrev-ref HEAD)
# shellcheck disable=SC2207
tags=($(git tag))

filename=./app/build.gradle.kts
filter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"

echo "HEAD is at $currentBranch -> $HEAD"

# Iterate over all tags
for tag in "${tags[@]}"
do
  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo "checking out tag: $tag ..."

  # Check out the tag
  git checkout --quiet "$tag"

  commitMessage=$(git show "$tag" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')

  echo "commit message: $commitMessage"

  echo "deleting tag $tag ..."
  git tag -d "$tag"

  # Replace the line in the file
  sed -i "s/$filter/\1$startingVersion/" $filename
  # sed -e "s/$filter/\1$startingVersion/" < $filename | grep -e $filter | tr -s ' \n' | xargs

  ((startingVersion+=1))

  # Commit the changes
  echo "adding $filename to git staging..."
  git add $filename

  echo "committing change: $(grep -e "$filter" < $filename | tr -s ' \n') ..."
  git commit -sm "updated tag $tag" -m "updated $filename::versionCode=$startingVersion in tag $tag"

  echo "adding tag $tag ..."
  git tag "$tag"

  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  echo
done

echo "switching back to $currentBranch -> $HEAD..."
git switch --quiet "$currentBranch"
