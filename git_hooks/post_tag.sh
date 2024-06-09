#!/bin/bash

if [ "$1" ]; then
  newTag=$1

  if git tag -l "$newTag" &>/dev/null; then
    buildGradleFile="app/build.gradle.kts"

    # shellcheck disable=SC2207
    tags=($(git tag -l --sort=-v:refname))
    prevTag=${tags[0]}
    versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
    versionNameFilter="\(versionName\s\+=\s\+\)\"\(.*\)\""

    prevTagVersionCode=$(git show "$prevTag:$buildGradleFile" | grep -m 1 versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
    prevTagVersionCodeLineNumber=$(git show "$prevTag:$buildGradleFile" | grep -nm 1 versionCode | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)
    prevTagVersionName=$(git show "$prevTag:$buildGradleFile" | grep -m 1 versionName | sed -e "s/$versionNameFilter/\2/" | xargs)
    prevTagVersionNameLineNumber=$(git show "$prevTag:$buildGradleFile" | grep -nm 1 versionName | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)

    currentVersionCode=$(grep -m 1 versionCode < $buildGradleFile | sed -e "s/$versionCodeFilter/\2/" | xargs)
    currentVersionCodeLineNumber=$(grep -nm 1 versionCode < $buildGradleFile | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)
    currentVersionName=$(grep -m 1 versionName < $buildGradleFile | sed -e "s/$versionNameFilter/\2/" | xargs)
    currentVersionNameLineNumber=$(grep -nm 1 versionName < $buildGradleFile | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)

    if [ "$currentVersionCode" -le "$prevTagVersionCode" ]; then
      echo "ERROR: current tag: $newTag has versionCode less than or equal to the previous tag: $prevTag, please change versionCode in "$buildGradleFile:$currentVersionCodeLineNumber""
      echo "$prevTag:$buildGradleFile:$prevTagVersionCodeLineNumber:versionCode = $prevTagVersionCode"
      echo "$newTag:$buildGradleFile:$currentVersionCodeLineNumber:versionCode = $currentVersionCode"
      echo
    fi

    if [ "$prevTagVersionName" == "$currentVersionName" ]; then
      echo "ERROR: current tag: $newTag has versionName the same as the previous tag: $prevTag, please change versionName in "$buildGradleFile:$currentVersionNameLineNumber""
      echo "$prevTag:$buildGradleFile:$prevTagVersionNameLineNumber:versionName = $prevTagVersionName"
      echo "$newTag:$buildGradleFile:$currentVersionNameLineNumber:versionName = $currentVersionName"
    fi

    exit 93

    gitTopLevel=$(git rev-parse --show-toplevel)

    if python "$gitTopLevel/git_hooks/update_publish_tags.py" "$gitTopLevel"; then
      echo "adding '$gitTopLevel/.github/workflows/publish_release.yml' to git staging..."

      git add "$gitTopLevel/.github/workflows/publish_release.yml"

      git commit --amend --no-edit
    fi;
  else
    echo fatal: tag "'$newTag'" already exists
  fi;

  exit 93
else
  git tag
fi;
