#!/bin/bash

# Instructions to set up the git alias:
# To create a git alias for this script locally in your repository, run the following command:
# git config --local alias.rtag '!sh git_hooks/post_tag.sh'
#
# After setting up the alias, you can use the command `git rtag <newTag>` to run this script.

# function to download yq binary for Windows
download_yq_windows() {
  echo "Downloading yq for Windows..."
  mkdir -p .git/bin
  curl --progress-bar -SL -o .git/bin/yq.exe https://github.com/mikefarah/yq/releases/latest/download/yq_windows_amd64.exe
}

update_publish_release_yaml_file_and_add_tag() {
  # Define yq variable
  yq=".git/bin/yq.exe"

  # check if yq exists in .git/bin folder
  if [ ! -x "$yq" ]; then
      download_yq_windows
  fi

  # update the publish_release.yml file with the latest tags from Git repository
  publishReleaseYaml=".github/workflows/publish_release_by_tag.yml"
  if [ -f "$publishReleaseYaml" ]; then
    tagsFromYaml="$("$yq" e '.on.workflow_dispatch.inputs.releaseTag.options' "$publishReleaseYaml" | tr -d '"\n' | sed -e "s/- //" | sed -e "s/- /, /gm")"
    updatedTags="$newTag $(git tag --sort=-creatordate | tr '\n' ' ' | xargs)"
    updatedTags="${updatedTags// /, }"

    if [ "$tagsFromYaml" != "${tags[*]}" ]; then
      echo
      echo "current tags in $publishReleaseYaml: [ $tagsFromYaml ]"

      "$yq" e -i '.on.workflow_dispatch.inputs.releaseTag.options = []' "$publishReleaseYaml"

      printf "adding tag: "
      for tag in $updatedTags; do
        printf "%s " "$tag"
        tag=${tag//,/}
        "$yq" e -i '.on.workflow_dispatch.inputs.releaseTag.options += [ "'"$tag"'" ] | .on.workflow_dispatch.inputs.releaseTag.options |= sort | .on.workflow_dispatch.inputs.releaseTag.options |= reverse' "$publishReleaseYaml"
      done
      echo

      echo "updated tags in $publishReleaseYaml to: [ $updatedTags ]!"

      currentCommitHash=$(git rev-parse HEAD)
      currentCommitMessage=$(git show "$currentCommitHash" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')
      isCurrentCommitOnRemote=$(git branch -r --contains "$currentCommitHash")

      echo

      if [ -n "$isCurrentCommitOnRemote" ]; then
        newVersionName="${newTag#v}"

        echo "commit '$currentCommitHash' is on the remote branch, creating a new bump commit..."
        echo

        git add "$buildGradleFile" $publishReleaseYaml
        git commit -sm "bump app version to $newVersionName" -m "$currentCommitMessage"
      else
        echo "commit '$currentCommitHash' is not on the remote branch, amending..."
        echo

        git add "$buildGradleFile" "$publishReleaseYaml"
        git commit --amend --no-edit
      fi

      echo
      echo "adding tag: $newTag..."
      git tag "$newTag"
      echo "tag: $newTag added!"
    else
      echo "tags in $publishReleaseYaml are up to date"
    fi

  scriptDir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd -P)
  sh $scriptDir/get_changelog.sh --tag "$newTag" --reference_tag "$prevTag" --write_changes --commit_changes

  else
    echo "ERROR: file $publishReleaseYaml not found"
    exit 93
  fi
}

# check if a new tag is provided as a command-line argument
if [ "$1" ]; then
  newTag=$1

  # check if the provided tag already exists in the Git repository
  if [ -n "$(git tag -l "$newTag")" ]; then
    echo "ERROR: tag $newTag already exists"
    exit 23
  else
    buildGradleFile="app/build.gradle.kts"

    # shellcheck disable=SC2207
    # get all tags in reverse order
    tags=($(git tag -l --sort=-v:refname))
    prevTag=${tags[0]}
    versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
    versionNameFilter="\(versionName\s\+=\s\+\)\"\(.*\)\""

    # extract the versionCode and versionName of the previous tag
    prevTagVersionCode=$(git show "$prevTag:$buildGradleFile" | grep -m 1 versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
    prevTagVersionCodeLineNumber=$(git show "$prevTag:$buildGradleFile" | grep -nm 1 versionCode | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)
    prevTagVersionName=$(git show "$prevTag:$buildGradleFile" | grep -m 1 versionName | sed -e "s/$versionNameFilter/\2/" | xargs)
    prevTagVersionNameLineNumber=$(git show "$prevTag:$buildGradleFile" | grep -nm 1 versionName | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)

    # extract the current versionCode and versionName from the buildGradleFile
    currentVersionCode=$(grep -m 1 versionCode < $buildGradleFile | sed -e "s/$versionCodeFilter/\2/" | xargs)
    currentVersionCodeLineNumber=$(grep -nm 1 versionCode < $buildGradleFile | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)
    currentVersionName=$(grep -m 1 versionName < $buildGradleFile | sed -e "s/$versionNameFilter/\2/" | xargs)
    currentVersionNameLineNumber=$(grep -nm 1 versionName < $buildGradleFile | grep -oe '[[:digit:]]\+:' | sed -e 's/\([[:digit:]]\+\):/\1/' | xargs)

    # initialize match flags
    versionCodeMatch=false
    versionNameMatch=false
    newTagVersionNameMatch=true

    # validate versionCode
    if [ "$currentVersionCode" -le "$prevTagVersionCode" ]; then
      echo "ERROR: current tag: $newTag has versionCode less than or equal to the previous tag: $prevTag, please change versionCode in "$buildGradleFile:"$currentVersionCodeLineNumber"""
      echo "$prevTag:$buildGradleFile:$prevTagVersionCodeLineNumber:versionCode = $prevTagVersionCode"
      echo "$newTag:$buildGradleFile:$currentVersionCodeLineNumber:versionCode = $currentVersionCode << should be $((currentVersionCode + 1))"
      versionCodeMatch=true
    fi

    # validate versionName
    if [ "$prevTagVersionName" == "$currentVersionName" ]; then
      if [ $versionCodeMatch == true ]; then
        echo
      fi
      echo "ERROR: current tag: $newTag has versionName the same as the previous tag: $prevTag, please change versionName in "$buildGradleFile:"$currentVersionNameLineNumber"""
      echo "$prevTag:$buildGradleFile:$prevTagVersionNameLineNumber:versionName = $prevTagVersionName"
      echo "$newTag:$buildGradleFile:$currentVersionNameLineNumber:versionName = $currentVersionName << should be $newTag"
      versionNameMatch=true
    fi

    # check if the new tag matches the current version name
    if [ "$newTag" != "v$currentVersionName" ]; then
      if [ $versionCodeMatch == true ] || [ $versionNameMatch == true ]; then
        echo
      fi
      echo "tag $newTag doesn't match $buildGradleFile:$currentVersionNameLineNumber:versionName = $currentVersionName"
      newTagVersionNameMatch=false
    fi

    # if there are any validation errors, exit with an error code
    if [ $versionCodeMatch == true ] || [ $versionNameMatch == true ] || [ $newTagVersionNameMatch == false ]; then
      echo
      # offer to update the mismatching values
      read -rp "Do you want to update the versionCode/versionName in $buildGradleFile? (Y/n): " choice
      choice=${choice:-Y} # Default to "Y" if no input is provided
      case "$choice" in
        [yY]*)
          if [ $versionCodeMatch == true ]; then
            newVersionCode=$((currentVersionCode + 1))

            echo
            echo "updating versionCode to versionCode = $newVersionCode ..."
            sed -i "${currentVersionCodeLineNumber}s/versionCode.*/versionCode = $((currentVersionCode + 1))/" $buildGradleFile
            echo "Updated versionCode to $newVersionCode in $buildGradleFile:$currentVersionCodeLineNumber!"
          fi
          if [ $versionNameMatch == true ]; then
            newVersionName="${newTag#v}"

            echo
            echo "updating versionName to versionName = ""$newVersionName"" ..."
            sed -i "${currentVersionNameLineNumber}s/versionName.*/versionName = \"$newVersionName\"/" $buildGradleFile
            echo "Updated versionName to $newVersionName in $buildGradleFile:$currentVersionNameLineNumber!"
          fi

          update_publish_release_yaml_file_and_add_tag
          ;;
        [nN]*)
          echo
          echo "ERROR: fix errors to add tag $newTag"
          echo "Aborting..."
          ;;
        *)
          echo
          echo "WTF! ERROR: choose [yY | nN] to fix/discard errors to add/not tag $newTag"
          ;;
      esac
    else
      update_publish_release_yaml_file_and_add_tag
    fi
  fi
else
  # if no new tag is provided, display all existing tags
  git tag
fi
