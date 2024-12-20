#!/bin/bash

gitTopLevel="$(git rev-parse --show-toplevel)"
versionCodeFilter="\(versionCode\s\+=\s\+\)\([[:digit:]]\+\)"
changelogsPath="$gitTopLevel/fastlane/metadata/android/en-US/changelogs"
tags=($(git tag))
changelogs=0

isWriteChanges=false
isCommitChanges=false

help() {
    echo "Usage: $0 [--write_changes] [--commit_changes]"
    echo
    echo "Arguments:"
    echo "  --write_changes        : Write the changes to a changelog file. The changelog file name is the <versionCode>.txt"
    echo "  --commit_changes       : Commit the changes to the repository."

    exit 1
}

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
    --help)
      help
      ;;
    *)
      echo "Unknown option: $1"
      help
      ;;
  esac
done

if [[ "$isWriteChanges" == true ]]; then
  if [ ! -d "$changelogsPath" ]; then
    echo "Creating changelogs folder..."
    mkdir -p "$changelogsPath"
  fi
fi

for index in $(seq $(( ${#tags[@]} - 1 )) -1 0)
do
  tag="${tags[$index]}"

  # Get the previous tag, or the first commit hash if index is 0
  if (( index > 0 )); then
    previousTag="${tags[$((index-1))]}"
  else
    previousTag=$(git rev-parse --short $(git rev-list --max-parents=0 HEAD))
  fi

  echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
  versionCode=$(git show "$tag:app/build.gradle.kts" | grep versionCode | sed -e "s/$versionCodeFilter/\2/" | xargs)
  commitMessage="$(git show "$tag" -s --format='%s%n%n%b' | sed -e 's/Change-Id:\s*\w\+//' | sed -e 's/Signed-off-by:\s*.*//')"

  commitHashesBetweenTags=$(git log "$previousTag".."$tag" --pretty=format:"%h")
  commitHashCount=$(echo "$commitHashesBetweenTags" | wc -l)

  if [[ $commitHashCount -gt 0 && -f "$changelogsPath/$versionCode.txt" && "$isWriteChanges" == true ]]; then
    echo "'$changelogsPath/$versionCode.txt' already exists, deleting..."
    rm "$changelogsPath/$versionCode.txt"
  fi

  echo "Generating Changelog between $tag and $previousTag..."
  echo "processing $commitHashCount commits..."
  for commitHash in $commitHashesBetweenTags
  do
    subject=$(git log --format=%s -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')
    body=$(git log --format=%b -n 1 $commitHash | sed -e 's/Change-Id:\s*.*//' | sed -e 's/Signed-off-by:\s*.*//' | sed -e 's/^[^a-zA-Z0-9]*//')

    subjects+=("$subject")
    bodies+=("$body")

    if [[ $changelogs -eq 1 && "$isWriteChanges" == true ]]; then
      echo "saving to '$changelogsPath/$versionCode.txt'..."
    fi

    echo "------------------------------"

    echo "Commit: $commitHash"
    echo "* $subject"

    if [ "$isWriteChanges" == true ]; then
      echo "* $subject" >> "$changelogsPath/$versionCode.txt"
    fi

    readarray -t lines <<< "$body"
    lineCount=${#lines[@]}
    for line in "${lines[@]}"; do
      if [[ -n "$line" ]]; then
        if [ $lineCount -gt 1 ]; then
          echo "   > - $line"

          if [ "$isWriteChanges" == true ]; then
            echo "   > - $line" >> "$changelogsPath/$versionCode.txt"
          fi
        else
          echo "   > $line"

          if [ "$isWriteChanges" == true ]; then
            echo "   > $line" >> "$changelogsPath/$versionCode.txt"
          fi
        fi
      else
        if [ $lineCount -eq 1 ]; then
          echo "   >"

          if [ "$isWriteChanges" == true ]; then
            echo "   >" >> "$changelogsPath/$versionCode.txt"
          fi
        fi
      fi

      ((changelogs+=1))
    done
  done

  if [[ -n "$previousTag" ]]; then
    fullChangelog="**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/compare/$previousTag...$tag"
  else
    fullChangelog="**Full Changelog**: https://github.com/abdalmoniem/Caffeinate/commits/$tag"
  fi

  echo
  echo "$fullChangelog"
  if [ "$isWriteChanges" == true ]; then
    echo >> "$changelogsPath/$versionCode.txt"
    echo "$fullChangelog" >> "$changelogsPath/$versionCode.txt"
  fi
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
